package com.merxury.blocker.ui.home.advsearch.local

import android.app.SearchManager
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.elvishew.xlog.XLog
import com.merxury.blocker.R
import com.merxury.blocker.databinding.LocalSearchFragmentBinding
import com.merxury.blocker.util.PreferenceUtil
import com.merxury.blocker.util.ToastUtil
import com.merxury.blocker.util.unsafeLazy
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LocalSearchFragment : Fragment() {
    private val logger = XLog.tag("AdvSearchFragment")
    private lateinit var binding: LocalSearchFragmentBinding
    private val viewModel: LocalSearchViewModel by viewModels()
    private val adapter by unsafeLazy { ExpandableSearchAdapter(this.lifecycleScope) }
    private var searchView: SearchView? = null
    private var isLoading = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = LocalSearchFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initListView()
        viewModel.load(requireContext())
        viewModel.filteredData.observe(viewLifecycleOwner) {
            binding.list.visibility = View.VISIBLE
            if (it.isEmpty()) {
                binding.searchNoResultHintGroup.visibility = View.VISIBLE
            } else {
                binding.searchNoResultHintGroup.visibility = View.GONE
            }
            binding.searchHintGroup.visibility = View.GONE
            adapter.updateData(it)
        }
        viewModel.operationDone.observe(viewLifecycleOwner) {
            val result = it.getContentIfNotHandled()
            if (result == true) {
                Toast.makeText(requireContext(), R.string.done, Toast.LENGTH_SHORT).show()
                adapter.notifyDataSetChanged()
            }
        }
        viewModel.loadingState.observe(viewLifecycleOwner) {
            logger.i("loadingState: $it")
            when (it) {
                is LocalSearchState.NotStarted -> {
                    setSearchIconVisibility(true)
                    binding.list.visibility = View.GONE
                    binding.loadingIndicatorGroup.visibility = View.GONE
                    binding.searchHintGroup.visibility = View.VISIBLE
                    binding.searchNoResultHintGroup.visibility = View.GONE
                }
                is LocalSearchState.Loading -> {
                    binding.searchNoResultHintGroup.visibility = View.GONE
                    binding.searchHintGroup.visibility = View.GONE
                    binding.loadingIndicatorGroup.visibility = View.VISIBLE
                    binding.list.visibility = View.GONE
                    binding.processingName.text = it.app?.packageName
                }
                is LocalSearchState.Finished -> {
                    binding.searchNoResultHintGroup.isVisible = (it.count == 0)
                    binding.list.isVisible = (it.count > 0)
                    binding.searchingHintGroup.visibility = View.GONE
                    binding.loadingIndicatorGroup.visibility = View.GONE
                    binding.searchHintGroup.visibility = View.GONE

                }
                is LocalSearchState.Searching -> {
                    binding.searchingHintGroup.visibility = View.VISIBLE
                    binding.searchNoResultHintGroup.visibility = View.GONE
                    binding.loadingIndicatorGroup.visibility = View.GONE
                    binding.searchHintGroup.visibility = View.GONE
                    binding.list.visibility = View.GONE
                }
                is LocalSearchState.Error -> {
                    it.exception.getContentIfNotHandled()?.let { error ->
                        showErrorDialog(error)
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.clear()
        inflater.inflate(R.menu.adv_search_menu, menu)
        initSearch(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.action_show_system_apps)?.isChecked =
            PreferenceUtil.getSearchSystemApps(requireContext())
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_show_system_apps -> handleSearchSystemAppClicked(item)
            R.id.action_refresh -> viewModel.load(requireContext(), forceInit = true)
            R.id.action_block_all -> batchDisable()
            R.id.action_enable_all -> batchEnable()
            else -> return false
        }
        return true
    }

    fun search(keyword: String) {
        if (isLoading) {
            logger.w("Can't search while loading")
            return
        }
        searchView?.setQuery(keyword, true)
    }

    private fun batchEnable() {
        Toast.makeText(
            requireContext(),
            R.string.enabling_components_please_wait,
            Toast.LENGTH_SHORT
        ).show()
        viewModel.doBatchOperation(true)
    }

    private fun batchDisable() {
        Toast.makeText(
            requireContext(),
            R.string.disabling_components_please_wait,
            Toast.LENGTH_SHORT
        ).show()
        viewModel.doBatchOperation(false)
    }

    override fun onDestroyView() {
        adapter.release()
        super.onDestroyView()
    }

    private fun initListView() {
        adapter.onSwitchClick = { component, checked ->
            logger.i("onSwitchClick: $component, $checked")
            viewModel.switchComponent(component.packageName, component.componentName, checked)
            component.ifwBlocked = !checked
            component.pmBlocked = !checked
        }
        binding.list.apply {
            setAdapter(this@LocalSearchFragment.adapter)
        }
    }

    private fun handleSearchSystemAppClicked(menuItem: MenuItem) {
        menuItem.isChecked = !menuItem.isChecked
        PreferenceUtil.setSearchSystemApps(requireContext(), menuItem.isChecked)
        viewModel.load(requireContext())
    }

    private fun initSearch(menu: Menu) {
        val searchItem = menu.findItem(R.id.action_search)
        searchView = searchItem?.actionView as? SearchView ?: return
        val searchManager =
            requireActivity().getSystemService(Context.SEARCH_SERVICE) as? SearchManager ?: return
        searchView?.setSearchableInfo(searchManager.getSearchableInfo(requireActivity().componentName))
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                logger.i("onQueryTextChange: $newText")
                try {
                    viewModel.filter(requireContext(), newText.orEmpty())
                } catch (e: Exception) {
                    logger.e("Invalid regex: $newText", e)
                    ToastUtil.showToast(R.string.invalid_regex, Toast.LENGTH_LONG)
                }
                return true
            }
        })
    }

    private fun showErrorDialog(e: Throwable) {
        val context = context
        if (context == null) {
            ToastUtil.showToast(getString(R.string.control_component_error_message, e.message))
        } else {
            AlertDialog.Builder(context)
                .setTitle(resources.getString(R.string.operation_failed))
                .setMessage(getString(R.string.control_component_error_message, e.message))
                .setPositiveButton(R.string.close) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
                .show()
        }
    }

    private fun setSearchIconVisibility(enabled: Boolean) {
        setHasOptionsMenu(enabled)
        activity?.invalidateOptionsMenu()
    }
}