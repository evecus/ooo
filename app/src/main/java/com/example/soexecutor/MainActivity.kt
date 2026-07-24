package com.example.soexecutor

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.soexecutor.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var copiedSoFile: File? = null
    private var terminalDialog: AlertDialog? = null
    private var terminalOutput: TextView? = null
    private var scrollView: ScrollView? = null
    private var runningProcess: java.lang.Process? = null

    private val selectFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { handleSelectedFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupViews()
    }

    private fun setupViews() {
        binding.btnSelectFile.setOnClickListener { openFilePicker() }
        binding.btnExecute.setOnClickListener { executeSoFile() }
    }

    private fun openFilePicker() {
        selectFileLauncher.launch(arrayOf("application/octet-stream", "application/x-sharedlib"))
    }

    private fun handleSelectedFile(uri: Uri) {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

        val fileName = getFileNameFromUri(uri)
        if (!fileName.lowercase().endsWith(".so")) {
            Toast.makeText(this, "请选择 .so 文件", Toast.LENGTH_SHORT).show()
            return
        }

        copiedSoFile = copyToPrivateDir(uri, fileName)
        copiedSoFile?.let {
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
        if (!privateDir.exists()) privateDir.mkdirs()

        val destFile = File(privateDir, fileName)
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
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
            showTerminalDialog()

            Thread {
                try {
                    val cmdParts = mutableListOf(file.absolutePath)
                    if (params.isNotEmpty()) {
                        cmdParts.addAll(params.split(" "))
                    }

                    val pb = ProcessBuilder(cmdParts)
                    pb.redirectErrorStream(true)
                    runningProcess = pb.start()

                    val input = runningProcess!!.getInputStream()
                    val buffer = ByteArray(1024)
                    var len: Int

                    while (runningProcess!!.isAlive) {
                        len = input.read(buffer)
                        if (len == -1) break
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
            if (terminalDialog != null && terminalDialog!!.isShowing) return@runOnUiThread

            val view = LayoutInflater.from(this).inflate(R.layout.dialog_terminal, null)
            terminalOutput = view.findViewById(R.id.tvTerminalOutput)
            scrollView = view.findViewById(R.id.scrollTerminal)
            val btnStop = view.findViewById<Button>(R.id.btnStop)

            terminalOutput?.movementMethod = ScrollingMovementMethod()

            btnStop.setOnClickListener { stopProcess() }

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
            scrollView?.post { scrollView?.fullScroll(View.FOCUS_DOWN) }
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
