package com.example.soexecutor

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.soexecutor.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var selectedSoFile: File? = null
    private var copiedSoFile: File? = null
    private var terminalDialog: AlertDialog? = null
    private var terminalOutput: TextView? = null
    private var scrollView: ScrollView? = null
    private var runningProcess: Process? = null

    private val selectFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            handleSelectedFile(it)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            openFilePicker()
        } else {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
    }

    private fun setupViews() {
        binding.btnSelectFile.setOnClickListener {
            checkPermissionAndPickFile()
        }

        binding.btnExecute.setOnClickListener {
            executeSoFile()
        }
    }

    private fun checkPermissionAndPickFile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_FILES) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_DOCUMENTS) == PackageManager.PERMISSION_GRANTED) {
                openFilePicker()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_FILES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                openFilePicker()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream", "application/x-sharedlib"))
        }
        selectFileLauncher.launch(intent)
    }

    private fun handleSelectedFile(uri: Uri) {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

        val fileName = getFileNameFromUri(uri)
        if (!fileName.lowercase().endsWith(".so")) {
            Toast.makeText(this, "请选择 .so 文件", Toast.LENGTH_SHORT).show()
            return
        }

        selectedSoFile = copyToPrivateDir(uri, fileName)
        copiedSoFile = selectedSoFile

        selectedSoFile?.let {
            binding.tvSelectedFile.text = getString(R.string.copied_to, it.absolutePath)
            binding.tvSelectedFile.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
            binding.btnExecute.isEnabled = true
            binding.tvStatus.text = getString(R.string.copy_success)
            Toast.makeText(this, R.string.copy_success, Toast.LENGTH_SHORT).show()
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        return try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && it.moveToFirst()) {
                    it.getString(nameIndex) ?: "unknown.so"
                } else {
                    "unknown.so"
                }
            } ?: "unknown.so"
        } catch (e: Exception) {
            "unknown.so"
        }
    }

    private fun copyToPrivateDir(uri: Uri, fileName: String): File? {
        val privateDir = File(filesDir, "so_files")
        if (!privateDir.exists()) {
            privateDir.mkdirs()
        }

        val destFile = File(privateDir, fileName)

        try {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile.setExecutable(true, false)
            return destFile
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, R.string.copy_failed, Toast.LENGTH_SHORT).show()
            return null
        }
    }

    private fun executeSoFile() {
        copiedSoFile?.let { file ->
            val params = binding.etParams.text.toString().trim()

            binding.btnExecute.isEnabled = false
            binding.progressBar.visibility = ProgressBar.VISIBLE
            binding.tvStatus.text = getString(R.string.executing)

            Thread {
                try {
                    System.load(file.absolutePath)

                    val command = if (params.isNotEmpty()) {
                        "${file.absolutePath} $params"
                    } else {
                        file.absolutePath
                    }

                    showTerminalDialog()

                    val processBuilder = ProcessBuilder(command.split(" ").toTypedArray())
                    processBuilder.redirectErrorStream(true)
                    runningProcess = processBuilder.start()

                    val inputStream = runningProcess!!.inputStream
                    val buffer = ByteArray(1024)
                    var len: Int

                    while (runningProcess!!.isAlive && inputStream.read(buffer).also { len = it } != -1) {
                        val output = String(buffer, 0, len)
                        appendTerminalOutput(output)
                    }

                    val exitCode = runningProcess!!.waitFor()
                    appendTerminalOutput("\n[进程已退出，退出码: $exitCode]")

                } catch (e: Exception) {
                    e.printStackTrace()
                    appendTerminalOutput("\n[错误: ${e.message}]")
                } finally {
                    runOnUiThread {
                        binding.progressBar.visibility = ProgressBar.GONE
                        binding.btnExecute.isEnabled = true
                    }
                    runningProcess = null
                }
            }.start()
        } ?: run {
            Toast.makeText(this, R.string.select_file_first, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showTerminalDialog() {
        runOnUiThread {
            val view = LayoutInflater.from(this).inflate(R.layout.dialog_terminal, null)
            terminalOutput = view.findViewById(R.id.tvTerminalOutput)
            scrollView = view.findViewById(R.id.scrollTerminal)
            val btnStop = view.findViewById<Button>(R.id.btnStop)

            terminalOutput!!.movementMethod = ScrollingMovementMethod()

            btnStop.setOnClickListener {
                stopProcess()
            }

            terminalDialog = AlertDialog.Builder(this, R.style.Theme_SoExecutor_TerminalDialog)
                .setView(view)
                .setCancelable(false)
                .create()

            terminalDialog!!.show()
        }
    }

    private fun appendTerminalOutput(text: String) {
        runOnUiThread {
            terminalOutput?.append(text)
            scrollView?.post {
                scrollView?.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    private fun stopProcess() {
        runningProcess?.destroy()
        runningProcess = null
        appendTerminalOutput("\n[进程已手动停止]")
        Toast.makeText(this, "已停止执行", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        runningProcess?.destroy()
    }
}