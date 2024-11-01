package com.xinbida.wukongdemo

import android.app.Activity
import android.content.Context
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import java.lang.ref.WeakReference

class GlideUtil {
    companion object{
        fun showAvatarImg(mContext: Context?, url: String?,  imageView: ImageView?) {
            if (mContext != null) {
                val weakReference = WeakReference(mContext)
                val context = weakReference.get()
                if (context is Activity) {
                    if (!context.isDestroyed) {
                        Glide.with(context).load(url).dontAnimate()
                            .apply(normalRequestOption())
                            .into(imageView!!)
                    }
                }
            }
        }


        private fun normalRequestOption(): RequestOptions {
            return RequestOptions()
                .error(R.drawable.default_view_bg)
                .placeholder(R.drawable.default_view_bg)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
        }
    }

}