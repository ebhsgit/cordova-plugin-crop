package com.jeduan.crop

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import com.canhub.cropper.*
import org.apache.cordova.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class CropPlugin : CordovaPlugin() {
    companion object {
        private const val TAG = "CropPlugin"

        private val outputFormat = Bitmap.CompressFormat.JPEG
    }

    private lateinit var cropImage: ActivityResultLauncher<CropImageContractOptions>

    private var cropResultHandler: ActivityResultCallback<CropImageView.CropResult>? = null

    override fun initialize(cordova: CordovaInterface, webView: CordovaWebView) {
        super.initialize(cordova, webView)

        cropImage = cordova.activity.registerForActivityResult(CropImageContract()) { result ->
            cropResultHandler?.onActivityResult(result)
        }
    }

    @Throws(JSONException::class)
    override fun execute(action: String, params: JSONArray, callbackContext: CallbackContext): Boolean {
        if (action == "cropImage") {
            val args = parseArgs(params)

            val inputUri = Uri.parse(args.imagePath)

            val pr = PluginResult(PluginResult.Status.NO_RESULT)
            pr.keepCallback = true
            callbackContext.sendPluginResult(pr)

            cordova.setActivityResultCallback(this)
            cropResultHandler = ActivityResultCallback<CropImageView.CropResult> {
                handleResult(callbackContext, it)
                cropResultHandler = null
            }

            cropImage.launch(
                options(uri = inputUri) {
                    setGuidelines(CropImageView.Guidelines.ON)
                    setOutputCompressFormat(outputFormat)
                    setOutputCompressQuality(args.quality)
                    setAspectRatio(1, 1)
                    setRequestedSize(args.targetWidth, args.targetHeight, CropImageView.RequestSizeOptions.RESIZE_INSIDE)
                }
            )

            return true
        }
        return false
    }

    private fun parseArgs(args: JSONArray): Args {
        val imagePath: String = args.getString(0)
        val options = args.getJSONObject(1)

        val quality = options.optInt("quality", 100)
        val targetWidth = options.optInt("targetWidth", -1)
        val targetHeight = options.optInt("targetHeight", -1)

        return Args(
            imagePath = imagePath,
            quality = quality,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
        )
    }

    private fun handleResult(callbackContext: CallbackContext, result: CropImageView.CropResult) {
        when {
            result.isSuccessful -> handleSuccess(callbackContext, result)
            result is CropImage.CancelledResult -> handleCancel(callbackContext)
            else -> handleError(callbackContext, result.error!!)
        }
    }

    private fun handleSuccess(callbackContext: CallbackContext, result: CropImageView.CropResult) {
        Log.d(TAG, "Crop success")
        val tempImagePath = result.getUriFilePath(cordova.context)
        Log.d(TAG, "Temp file Path ${tempImagePath}")
        val fileUri = "file://${tempImagePath}?${System.currentTimeMillis()}"
        callbackContext.success(fileUri)
    }

    private fun handleCancel(callbackContext: CallbackContext) {
        Log.d(TAG, "Cropping image was cancelled by the user")

        try {
            val err = JSONObject()
            err.put("message", "User cancelled")
            err.put("code", "userCancelled")
            callbackContext.error(err)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private fun handleError(callbackContext: CallbackContext, error: Throwable) {
        Log.e(TAG, "Cropping image failed", error)

        try {
            val err = JSONObject()
            err.put("message", error.message)
            err.put("code", "crop-error")
            callbackContext.error(err)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }
}

private class Args(
    val imagePath: String,
    val quality: Int,
    val targetWidth: Int,
    val targetHeight: Int,
)