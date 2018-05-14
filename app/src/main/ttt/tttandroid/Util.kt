package ttt.tttandroid

import android.content.Context
import android.widget.Toast

object Codes {
    const val REQUEST_ENABLE_BT = 1
    const val REQUEST_COARSE_LOC = 2

    const val PICK_BT_DEVICE = 3
    const val BT_DEVICE_EXTRA = "ttt.tttandroid.extra.BT_DEVICE"
}

fun Context.showAsToast(text: String) {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}

fun Context.showAsToast(resId: Int) {
    Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
}

//avoid "condition always true" warnings
val MOCK_BT = "false".toBoolean()