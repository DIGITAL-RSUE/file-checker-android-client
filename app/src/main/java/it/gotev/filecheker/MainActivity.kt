package it.gotev.filecheker

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.text.Spanned
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.alpha
import com.beust.klaxon.Klaxon
import net.gotev.uploadservice.data.UploadInfo
import net.gotev.uploadservice.exceptions.UploadError
import net.gotev.uploadservice.exceptions.UserCancelledUploadException
import net.gotev.uploadservice.network.ServerResponse
import net.gotev.uploadservice.observer.request.RequestObserverDelegate
import net.gotev.uploadservice.protocols.multipart.MultipartUploadRequest


class MainActivity : AppCompatActivity() {

    companion object {
        // Every intent for result needs a unique ID in your app.
        // Choose the number which is good for you, here I'll use a random one.
        const val pickFileRequestCode = 42
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val progressBar =  findViewById<ProgressBar>(R.id.progressBar)
        progressBar.visibility = View.INVISIBLE
        findViewById<Button>(R.id.uploadButton).setOnClickListener {
            pickFile()
        }
    }

    // Pick a file with a content provider
    fun pickFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            // Filter to only show results that can be "opened", such as files
            addCategory(Intent.CATEGORY_OPENABLE)
            // search for all documents available via installed storage providers
            type = "*/*"
            // obtain permission to read and persistable permission
            flags = (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, pickFileRequestCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // The ACTION_OPEN_DOCUMENT intent was sent with the request code
        // READ_REQUEST_CODE. If the request code seen here doesn't match, it's the
        // response to some other intent, and the code below shouldn't run at all.
        if (requestCode == pickFileRequestCode && resultCode == Activity.RESULT_OK) {
            data?.let {
                onFilePicked(it.data.toString())
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    fun onFilePicked(filePath: String) {
        val response = MultipartUploadRequest(this, serverUrl = "http://89.108.71.253/api/v1/file-checker/")
            .setMethod("POST")
            .addFileToUpload(
                filePath = filePath,
                parameterName = "checkedFile"
            )
            .subscribe(context = this, lifecycleOwner = this, delegate = object : RequestObserverDelegate {
                override fun onProgress(context: Context, uploadInfo: UploadInfo) {
//                    Toast.makeText(context,"Ожидайте", Toast.LENGTH_LONG).show()
                    val progressBar =  findViewById<ProgressBar>(R.id.progressBar)
                    progressBar.visibility = View.VISIBLE
                }

                override fun onSuccess(
                    context: Context,
                    uploadInfo: UploadInfo,
                    serverResponse: ServerResponse
                ) {
                    val result = Klaxon()
                        .parse<FileResult>(serverResponse.bodyString)

                    if (result != null && result.checkedFile.isNotEmpty()) {
                        var msg = ""
                        for (str in result.checkedFile){
                            msg += "$str<br/>"
                        }
                        var strMessage = Html.fromHtml(msg)
                        alertDialog(strMessage)
                    } else {
                        alertDialog(Html.fromHtml("Угроз не обнаружено!"))
                    }
                }

                override fun onError(
                    context: Context,
                    uploadInfo: UploadInfo,
                    exception: Throwable
                ) {
                    when (exception) {
                        is UserCancelledUploadException -> {
                            Log.e("RECEIVER", "Error, user cancelled upload: $uploadInfo")
                            Toast.makeText(context,"Возникла ошибка", Toast.LENGTH_LONG).show()
                        }

                        is UploadError -> {
                            Log.e("RECEIVER", "Error, upload error: ${exception.serverResponse}")
                            Toast.makeText(context,"Возникла ошибка", Toast.LENGTH_LONG).show()                        }

                        else -> {
                            Log.e("RECEIVER", "Error: $uploadInfo", exception)
                            Toast.makeText(context,"Возникла ошибка", Toast.LENGTH_LONG).show()                        }
                    }
                }

                override fun onCompleted(context: Context, uploadInfo: UploadInfo) {
//                    Toast.makeText(context,"Complete: ${uploadInfo}", Toast.LENGTH_LONG).show()
                    val progressBar =  findViewById<ProgressBar>(R.id.progressBar)
                    progressBar.visibility = View.INVISIBLE
                }

                override fun onCompletedWhileNotObserving() {
                    // do your thing
                }
            })

    }
    private fun alertDialog(message: Spanned) {
        val dialog: AlertDialog.Builder = AlertDialog.Builder(this)
        dialog.setMessage(message)
        dialog.setTitle("Результат проверки файла")
        dialog.setPositiveButton("Ок",
            DialogInterface.OnClickListener { dialog, which ->

            })
        val alertDialog: AlertDialog = dialog.create()
        alertDialog.show()
    }
}
class FileResult(val checkedFile: Array<String>)