package com.haoge.easyandroid.easy

import android.app.Activity
import android.app.Fragment
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import java.io.File


/**
 * 创建日期：2018/8/21 0021on 下午 4:40
 * 描述：图片选择工具类
 * @author：Vincent
 * 加工：3332168769
 * 备注：参考自 CSDN_LQR 的 LQRPhotoSelectUtils
 */
class EasyPhoto(val isCrop:Boolean = false) {

    /**
     * 设置图片选择结果回调
     */
    private var callback: ((file: File?) -> Unit)? = null

    /**
     * 拍照或剪切后图片的存放位置(参考file_provider_paths.xml中的路径)
     */
    private var mImgPath:File? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    fun setCallback(callback: ((file: File?) -> Unit)): EasyPhoto {
        this.callback = callback
        return this
    }

    /**
     * 修改图片的存储路径（默认的图片存储路径是SD卡上 Android/data/应用包名/时间戳.jpg）
     *
     * @param imgPath 图片的存储路径（包括文件名和后缀）
     */
    fun setImgPath(imgPath: String): EasyPhoto {
        this.mImgPath = File(imgPath)
        this.mImgPath?.parentFile?.mkdirs()
        return this
    }

    /**
     * 从图库获取
     */
    fun selectPhoto(activity: Activity) {
        val intent = Intent(Intent.ACTION_PICK, null)
        intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
        if (Looper.myLooper() == Looper.getMainLooper()) {
            selectPhotoInternal(intent, activity)
        } else {
            mainHandler.post { selectPhotoInternal(intent, activity) }
        }
    }

    private fun selectPhotoInternal(intent: Intent, activity: Activity) {
        PhotoFragment.findOrCreate(activity).start(intent, PhotoFragment.REQ_SELECT_PHOTO) { requestCode: Int, data: Intent? ->
            if (requestCode == PhotoFragment.REQ_SELECT_PHOTO) {
                data ?: return@start

                val sourceUri = data.data
                val projection = arrayOf(MediaStore.Images.Media.DATA)
                @Suppress("DEPRECATION")
                val cursor = activity.managedQuery(sourceUri, projection, null, null, null)

                val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                cursor.moveToFirst()
                val imgPath = cursor.getString(columnIndex)
                val inputFile = File(imgPath)

                if (isCrop) {//裁剪
                    zoomPhoto(inputFile, mImgPath?:File(generateImagePath(activity)), activity)
                } else {//不裁剪
                    callback?.invoke(inputFile)
                }
            }
        }
    }

    /**
     * 拍照获取
     */
    fun takePhoto(activity: Activity) {
        val imgFile = if (isCrop) {
            File(generateImagePath(activity))
        } else {
            mImgPath?: File(generateImagePath(activity))
        }

        val imgUri = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Uri.fromFile(imgFile)
        } else {
            //兼容android7.0 使用共享文件的形式
            val contentValues = ContentValues(1)
            contentValues.put(MediaStore.Images.Media.DATA, imgFile.absolutePath)
            activity.application.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        }

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imgUri)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            takePhotoInternal(imgFile,intent, activity)
        } else {
            mainHandler.post { takePhotoInternal(imgFile,intent, activity) }
        }
    }

    private fun takePhotoInternal(takePhotoPath:File, intent: Intent, activity: Activity) {
        val fragment = PhotoFragment.findOrCreate(activity)
        fragment.start(intent, PhotoFragment.REQ_TAKE_PHOTO) { requestCode: Int, _: Intent? ->
            if (requestCode == PhotoFragment.REQ_TAKE_PHOTO) {
                if (isCrop) {
                    zoomPhoto(takePhotoPath, mImgPath?: File(generateImagePath(activity)), activity)
                } else {
                    callback?.invoke(takePhotoPath)
                }
            }
        }
    }

    /***
     * 图片裁剪
     */
    private fun zoomPhoto(inputFile: File?, outputFile: File, activity: Activity) {
        val intent = Intent("com.android.camera.action.CROP")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.setDataAndType(getImageContentUri(activity, inputFile), "image/*")
        } else {
            intent.setDataAndType(Uri.fromFile(inputFile), "image/*")
        }
        intent.putExtra("crop", "true")

        // 是否返回uri
        intent.putExtra("return-data", false)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(outputFile))
        intent.putExtra("outputFormat", Bitmap.CompressFormat.JPEG.toString())

        zoomPhotoInternal(outputFile,intent, activity)
    }

    private fun zoomPhotoInternal(outputFile: File?,intent: Intent, activity: Activity) {
        PhotoFragment.findOrCreate(activity).start(intent, PhotoFragment.REQ_ZOOM_PHOTO) { requestCode: Int, data: Intent? ->
            if (requestCode == PhotoFragment.REQ_ZOOM_PHOTO) {
                data ?: return@start
                callback?.invoke(outputFile)
            }
        }
    }

    /**
     * 产生图片的路径，带文件夹和文件名，文件名为当前毫秒数
     */
    private fun generateImagePath(activity: Activity): String {
        val file =  getExternalStoragePath(activity) + File.separator + System.currentTimeMillis().toString() + ".jpg"

        File(file).parentFile.mkdirs()
        return file
    }

    /**
     * 获取SD下的应用目录
     */
    private fun getExternalStoragePath(activity: Activity): String {
        val sb = StringBuilder()
        sb.append(Environment.getExternalStorageDirectory().absolutePath)
        sb.append(File.separator)
        sb.append("Android/data/pics" + activity.packageName)
        sb.append(File.separator)

        return sb.toString()
    }

    /**
     * 安卓7.0裁剪根据文件路径获取uri
     */
    private fun getImageContentUri(context: Context, imageFile: File?): Uri? {
        val filePath = imageFile?.absolutePath
        val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                MediaStore.Images.Media.DATA + "=? ",
                arrayOf(filePath), null)

        cursor.use { _ ->
            return if (cursor != null && cursor.moveToFirst()) {
                val id = cursor.getInt(cursor
                        .getColumnIndex(MediaStore.MediaColumns._ID))
                val baseUri = Uri.parse("content://media/external/images/media")
                Uri.withAppendedPath(baseUri, "" + id)
            } else {
                imageFile?.let {
                    if (it.exists()) {
                        val values = ContentValues()
                        values.put(MediaStore.Images.Media.DATA, filePath)
                        context.contentResolver.insert(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    } else {
                        null
                    }
                }

            }
        }
    }

    /**
     * 用于获取图片的Fragment
     */
    class PhotoFragment : Fragment() {
        /**
         * Fragment处理照片后返回接口
         */
        private var callback: ((requestCode: Int, intent: Intent?) -> Unit)? = null

        /**
         * 开启系统相册
         *      裁剪图片、打开相册选择单张图片、拍照
         */
        fun start(intent: Intent, requestCode: Int, callback: ((requestCode: Int, intent: Intent?) -> Unit)) {
            this.callback = callback
            startActivityForResult(intent, requestCode)
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)
            if (resultCode == Activity.RESULT_OK) {
                callback?.invoke(requestCode, data)
            }
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            retainInstance = true
        }

        companion object {
            const val REQ_TAKE_PHOTO = 10001
            const val REQ_SELECT_PHOTO = 10002
            const val REQ_ZOOM_PHOTO = 10003
            private const val TAG = "EasyPhoto:PhotoFragment"

            @JvmStatic
            fun findOrCreate(activity: Activity): PhotoFragment {
                var fragment: PhotoFragment? = activity.fragmentManager.findFragmentByTag(TAG) as PhotoFragment?
                if (fragment == null) {
                    fragment = PhotoFragment()
                    activity.fragmentManager.beginTransaction()
                            .add(fragment, TAG)
                            .commitAllowingStateLoss()
                    activity.fragmentManager.executePendingTransactions()
                }
                return fragment
            }
        }
    }
}