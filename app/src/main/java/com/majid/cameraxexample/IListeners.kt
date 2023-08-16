package com.majid.cameraxexample

import java.io.File

class IListeners {
    interface OnVideoSavedCallback {
        fun onVideoSaved(videoFile: File)
    }
}