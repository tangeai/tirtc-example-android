package com.tange.ai.tirtc.examples.client

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

class ClientSquareFrameLayout
  @JvmOverloads
  constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
  ) : FrameLayout(context, attrs, defStyleAttr) {
    override fun onMeasure(
      widthMeasureSpec: Int,
      heightMeasureSpec: Int,
    ) {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec)
      val size = measuredWidth
      setMeasuredDimension(size, size)
      val childMeasureSpec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
      super.onMeasure(childMeasureSpec, childMeasureSpec)
    }
  }
