package com.frafio.myfinance.ui.home.budget

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.LinearLayoutManager
import com.frafio.myfinance.R
import com.frafio.myfinance.data.enums.db.PurchaseCode
import com.frafio.myfinance.data.models.Purchase
import com.frafio.myfinance.data.models.PurchaseResult
import com.frafio.myfinance.databinding.FragmentBudgetBinding
import com.frafio.myfinance.ui.BaseFragment
import com.frafio.myfinance.ui.home.HomeActivity
import com.frafio.myfinance.utils.clearText
import com.frafio.myfinance.utils.doubleToString
import com.frafio.myfinance.utils.hideSoftKeyboard

class BudgetFragment : BaseFragment(), BudgetListener, IncomeInteractionListener {
    private lateinit var binding: FragmentBudgetBinding
    private val viewModel by viewModels<BudgetViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_budget, container, false)

        viewModel.listener = this

        binding.viewModel = viewModel
        binding.lifecycleOwner = viewLifecycleOwner

        viewModel.updateLocalIncomeList()
        viewModel.getMonthlyBudgetFromDb()

        viewModel.incomes.observe(viewLifecycleOwner) { incomes ->
            val nl = incomes.map { i -> i.copy() }
            binding.budgetRecycleView.also {
                if (it.adapter == null) {
                    it.adapter = IncomeAdapter(nl, this)
                } else {
                    (it.adapter as IncomeAdapter).updateData(nl)
                }
            }
        }

        viewModel.monthlyBudget.observe(viewLifecycleOwner) { budget ->
            binding.monthlyBudgetDeleteBtn.isEnabled = budget != 0.0
        }

        binding.monthlyBudgetEditBtn.setOnClickListener {
            if (binding.monthlyBudgetTV.isVisible) {
                // show edit interface
                if (binding.monthlyBudgetTV.text == "0.00") {
                    binding.monthlyBudgetET.clearText()
                } else {
                    binding.monthlyBudgetET.setText(binding.monthlyBudgetTV.text)
                }
                binding.monthlyBudgetTV.visibility = View.GONE
                binding.monthlyBudgetET.visibility = View.VISIBLE
                binding.monthlyBudgetEditBtn.icon =
                    ContextCompat.getDrawable(requireContext(), R.drawable.ic_clear)
                binding.monthlyBudgetDeleteBtn.icon =
                    ContextCompat.getDrawable(requireContext(), R.drawable.ic_check)
                binding.monthlyBudgetDeleteBtn.isEnabled = false
            } else {
                // cancel any modifications
                binding.monthlyBudgetTV.visibility = View.VISIBLE
                binding.monthlyBudgetET.visibility = View.GONE
                requireContext().hideSoftKeyboard(requireView().rootView)
                binding.monthlyBudgetEditBtn.icon =
                    ContextCompat.getDrawable(requireContext(), R.drawable.ic_create)
                binding.monthlyBudgetDeleteBtn.icon =
                    ContextCompat.getDrawable(requireContext(), R.drawable.ic_delete)
                binding.monthlyBudgetDeleteBtn.isEnabled = binding.monthlyBudgetTV.text != "0.00"
            }
        }

        binding.monthlyBudgetDeleteBtn.setOnClickListener {
            if (binding.monthlyBudgetTV.isVisible) {
                // delete budget
                viewModel.updateMonthlyBudget(0.0, true)
            } else {
                // confirm modifications
                val budget = binding.monthlyBudgetET.text.toString().toDouble()
                viewModel.updateMonthlyBudget(budget, true)
            }
        }

        binding.monthlyBudgetET.doOnTextChanged { text, _, _, _ ->
            val newBudget = doubleToString(text?.trim().toString().toDoubleOrNull() ?: 0.0)
            val previousBudget = binding.monthlyBudgetTV.text.toString()
            binding.monthlyBudgetDeleteBtn.isEnabled =
                newBudget != "0.00" && newBudget != previousBudget
        }

        return binding.root
    }

    override fun onCompleted(response: LiveData<PurchaseResult>, previousBudget: Double?) {
        response.observe(viewLifecycleOwner) { result ->
            when (result.code) {
                PurchaseCode.BUDGET_UPDATE_SUCCESS.code -> {
                    viewModel.updateMonthlyBudgetFromStorage()
                    if (!binding.monthlyBudgetTV.isVisible) {
                        binding.monthlyBudgetTV.visibility = View.VISIBLE
                        binding.monthlyBudgetET.visibility = View.GONE
                        requireContext().hideSoftKeyboard(requireView().rootView)
                        binding.monthlyBudgetEditBtn.icon =
                            ContextCompat.getDrawable(requireContext(), R.drawable.ic_create)
                        binding.monthlyBudgetDeleteBtn.icon =
                            ContextCompat.getDrawable(requireContext(), R.drawable.ic_delete)
                    }
                    previousBudget?.let {
                        (activity as HomeActivity).showSnackBar(
                            result.message,
                            getString(R.string.cancel)
                        ) {
                            viewModel.updateMonthlyBudget(previousBudget)
                        }
                    }
                }

                else -> {
                    (activity as HomeActivity).showSnackBar(result.message)
                }
            }
        }
    }

    override fun scrollUp() {
        super.scrollUp()
        binding.budgetScrollView.scrollTo(0, 0)
    }

    override fun onItemInteraction(interactionID: Int, purchase: Purchase, position: Int) {}

    fun scrollIncomesTo(position: Int) {
        (binding.budgetRecycleView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
            position,
            0
        )
    }

    fun refreshData() {
        viewModel.updateLocalIncomeList()
        viewModel.getMonthlyBudgetFromDb()
        scrollUp()
    }
}