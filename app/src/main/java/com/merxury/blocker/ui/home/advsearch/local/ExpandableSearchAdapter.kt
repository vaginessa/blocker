package com.merxury.blocker.ui.home.advsearch.local

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import com.elvishew.xlog.XLog
import com.google.android.material.switchmaterial.SwitchMaterial
import com.merxury.blocker.R
import com.merxury.blocker.data.app.AppComponent
import com.merxury.blocker.data.app.InstalledApp
import com.merxury.blocker.ui.detail.component.ComponentData
import com.merxury.blocker.util.AppIconCache
import com.merxury.libkit.entity.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ExpandableSearchAdapter(private val lifecycleScope: LifecycleCoroutineScope) :
    BaseExpandableListAdapter() {
    private val logger = XLog.tag("ExpandableSearchAdapter")
    private var appList = listOf<InstalledApp?>()
    private var data = mapOf<InstalledApp?, List<AppComponent>>()
    private var loadIconJob: Job? = null
    var onSwitchClick: ((AppComponent, Boolean) -> Unit)? = null

    fun updateData(newData: Map<InstalledApp?, List<AppComponent>>) {
        appList = newData.keys.toList()
        data = newData
        notifyDataSetChanged()
    }

    fun release() {
        if (loadIconJob?.isActive == true) {
            loadIconJob?.cancel()
        }
    }

    override fun getGroup(groupPosition: Int): Any? {
        return appList[groupPosition]
    }

    override fun getGroupCount(): Int {
        return appList.size
    }

    override fun getGroupId(groupPosition: Int): Long {
        return appList[groupPosition]?.packageName.hashCode().toLong()
    }

    override fun getChild(groupPosition: Int, childPosition: Int): Any? {
        val app = appList.getOrNull(groupPosition)
        return data[app]?.getOrNull(childPosition)
    }

    override fun getChildId(groupPosition: Int, childPosition: Int): Long {
        val app = appList.getOrNull(groupPosition)
        val child = data[app]?.getOrNull(childPosition)
        return child?.componentName?.hashCode()?.toLong() ?: 0
    }

    override fun getChildrenCount(groupPosition: Int): Int {
        val app = appList.getOrNull(groupPosition)
        val child = data[app]
        return child?.size ?: 0
    }

    override fun getGroupView(
        groupPosition: Int,
        isExpanded: Boolean,
        convertView: View?,
        parent: ViewGroup?
    ): View {
        val app = appList[groupPosition]
        val components = data[app] ?: listOf()
        val context = parent?.context ?: throw IllegalStateException("Context is null")
        val view = LayoutInflater.from(context)
            .inflate(R.layout.search_app_header, parent, false)
        view.findViewById<TextView>(R.id.app_name).text = app?.label
        view.findViewById<ImageView>(R.id.icon).apply {
            if (getTag(R.id.app_item_icon_id) != app?.packageName) {
                setTag(R.id.app_item_icon_id, app?.packageName)
                loadIcon(context, app, this)
            }
        }
        view.findViewById<TextView>(R.id.search_count).text = components.size.toString()
        return view
    }

    override fun hasStableIds(): Boolean {
        return true
    }

    override fun isChildSelectable(groupPosition: Int, childPosition: Int): Boolean {
        return true
    }

    override fun getChildView(
        groupPosition: Int,
        childPosition: Int,
        isLastChild: Boolean,
        convertView: View?,
        parent: ViewGroup?
    ): View {
        val app = appList.getOrNull(groupPosition)
        val child = data[app]?.getOrNull(childPosition) ?: throw RuntimeException("Child is null")
        val context = parent?.context
        val view = LayoutInflater.from(context)
            .inflate(R.layout.search_app_component, parent, false)
        view.findViewById<TextView>(R.id.component_name).text = child.componentName
        val switch = view.findViewById<SwitchMaterial>(R.id.component_switch)
        switch.setOnCheckedChangeListener(null)
        switch.isChecked = !(child.pmBlocked || child.ifwBlocked)
        switch.setOnCheckedChangeListener { _, isChecked ->
            onSwitchClick?.invoke(child, isChecked)
        }
        return view
    }

    private fun loadIcon(context: Context, app: InstalledApp?, view: ImageView) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val packageInfo = context.packageManager.getPackageInfo(app?.packageName ?: "", 0)
                val appInfo = packageInfo?.applicationInfo ?: run {
                    logger.e("Application info is null, packageName: ${app?.packageName}")
                    return@launch
                }
                loadIconJob = AppIconCache.loadIconBitmapAsync(
                    context,
                    appInfo,
                    appInfo.uid / 100000,
                    view
                )
            } catch (e: Exception) {
                logger.e("Failed to load icon, packageName: ${app?.packageName}", e)
            }
        }
    }
}