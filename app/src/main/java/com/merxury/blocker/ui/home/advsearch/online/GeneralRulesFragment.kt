package com.merxury.blocker.ui.home.advsearch.online

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.elvishew.xlog.XLog
import com.merxury.blocker.R
import com.merxury.blocker.data.Resource
import com.merxury.blocker.databinding.GeneralRulesFragmentBinding
import com.merxury.blocker.ui.home.advsearch.ILocalSearchHost
import com.merxury.blocker.util.unsafeLazy
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GeneralRulesFragment : Fragment() {
    private val logger = XLog.tag("GeneralRulesFragment")
    private val viewModel: GeneralRulesViewModel by viewModels()
    private lateinit var binding: GeneralRulesFragmentBinding
    private val adapter by unsafeLazy { GeneralRulesAdapter() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = GeneralRulesFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initRecyclerView()
        initSwipeLayout()
        viewModel.rules.observe(viewLifecycleOwner) {
            if (it == null) {
                logger.e("rules is null")
                return@observe
            }
            when (it.status) {
                Resource.Status.SUCCESS -> {
                    logger.d("Load rules successfully")
                    binding.swipeLayout.isRefreshing = false
                    val data = it.data
                    if (data == null) {
                        logger.e("rules is null")
                        return@observe
                    }
                    adapter.updateData(data)
                }
                Resource.Status.ERROR -> {
                    logger.e("Can't fetch rules: ${it.message}")
                    showErrorDialog(it.message)
                    binding.swipeLayout.isRefreshing = false
                }
                Resource.Status.LOADING -> {
                    logger.d("Load rules")
                    binding.swipeLayout.isRefreshing = true
                }
            }
        }
    }

    private fun showErrorDialog(message: String?) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.oops))
            .setMessage(getString(R.string.error_occurred_with_message, message))
            .setPositiveButton(R.string.close) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
            .show()
    }

    private fun initRecyclerView() {
        binding.recyclerView.adapter = adapter
        adapter.onSearchClickListener = { rule ->
            logger.d("rule is clicked: $rule")
            val keyword = rule.searchKeyword.joinToString()
            (parentFragment as? ILocalSearchHost)?.searchLocal(keyword)
        }
    }

    private fun initSwipeLayout() {
        binding.swipeLayout.setOnRefreshListener {
            viewModel.refresh()
        }
    }
}