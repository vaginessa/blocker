package com.merxury.blocker.provider

import androidx.annotation.Keep

@Keep
data class ShareCmpInfo(
  val pkg: String,
  val components: List<Component>
) {
  @Keep
  data class Component(
    val type: String,
    val name: String,
    val block: Boolean
  )
}
