package com.tange.ai.tirtc.examples.server

import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity

internal class ServerLoadingDialog(
  activity: AppCompatActivity,
) : Dialog(activity) {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.dialog_loading)
    window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
    window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    setCancelable(false)
    setCanceledOnTouchOutside(false)
  }
}
