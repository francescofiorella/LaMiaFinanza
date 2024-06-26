package com.frafio.myfinance.data.storages

import com.frafio.myfinance.data.enums.db.DbPurchases
import com.frafio.myfinance.data.models.Purchase
import com.google.firebase.firestore.QuerySnapshot
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object PurchaseStorage {
    var purchaseList: MutableList<Purchase> = mutableListOf()
    var incomeList: MutableList<Purchase> = mutableListOf()
    var monthlyBudget: Double = 0.0

    fun resetPurchaseList() {
        purchaseList = mutableListOf()
    }

    fun populateListFromSnapshot(queryDocumentSnapshots: QuerySnapshot) {
        resetPurchaseList()
        // Create total for the local list
        var total: Purchase? = null
        // Used to keep the order
        var currentPurchases = mutableListOf<Purchase>()

        queryDocumentSnapshots.forEach { document ->
            val purchase = document.toObject(Purchase::class.java)
            // set id
            purchase.id = document.id
            val todayDate = LocalDate.now()
            val purchaseDate = purchase.getLocalDate()
            var prevDate: LocalDate? = if (total == null) // se primo acquisto
                null
            else
                total!!.getLocalDate() // ultimo totale

            // se è < today and non hai fatto today
            // quindi se purchase < today and (totale == null or totale > today)
            // se prevDate > today and purchase < today
            if ((prevDate == null || ChronoUnit.DAYS.between(prevDate, todayDate) < 0) &&
                ChronoUnit.DAYS.between(purchaseDate, todayDate) > 0
            ) {
                if (currentPurchases.isNotEmpty()) {
                    purchaseList.add(total!!)
                    currentPurchases.forEach { cPurchase ->
                        purchaseList.add(cPurchase)
                    }
                    currentPurchases = mutableListOf()
                }
                // Aggiungi totale a 0 per oggi
                val totId = "${todayDate.dayOfMonth}_${todayDate.monthValue}_${todayDate.year}"
                total = Purchase(
                    name = DbPurchases.NAMES.TOTAL.value,
                    price = 0.0,
                    year = todayDate.year,
                    month = todayDate.monthValue,
                    day = todayDate.dayOfMonth,
                    category = DbPurchases.CATEGORIES.TOTAL.value,
                    id = totId
                )
                purchaseList.add(total!!)
                prevDate = total!!.getLocalDate()
            }

            if (prevDate == null) { // If is the first total
                currentPurchases.add(purchase)
                total = Purchase(
                    name = DbPurchases.NAMES.TOTAL.value,
                    price = purchase.price,
                    year = purchase.year,
                    month = purchase.month,
                    day = purchase.day,
                    category = DbPurchases.CATEGORIES.TOTAL.value,
                    id = purchase.getTotalId()
                )
            } else if (total!!.id == purchase.getTotalId()) { // If the total should be updated
                currentPurchases.add(purchase)
                total = Purchase(
                    name = DbPurchases.NAMES.TOTAL.value,
                    price = total!!.price!! + purchase.price!!,
                    year = total!!.year,
                    month = total!!.month,
                    day = total!!.day,
                    category = DbPurchases.CATEGORIES.TOTAL.value,
                    id = total!!.getTotalId()
                )
            } else { // If we need a new total
                // Update the local list with previous day purchases
                if (currentPurchases.isNotEmpty()) {
                    purchaseList.add(total!!)
                    currentPurchases.forEach { cPurchase ->
                        purchaseList.add(cPurchase)
                    }
                    currentPurchases = mutableListOf()
                }
                currentPurchases.add(purchase)
                // Create new total
                total = Purchase(
                    name = DbPurchases.NAMES.TOTAL.value,
                    price = purchase.price,
                    year = purchase.year,
                    month = purchase.month,
                    day = purchase.day,
                    category = DbPurchases.CATEGORIES.TOTAL.value,
                    id = purchase.getTotalId()
                )
            }
        }
        if (currentPurchases.isNotEmpty()) {
            purchaseList.add(total!!)
            currentPurchases.forEach { cPurchase ->
                purchaseList.add(cPurchase)
            }
            currentPurchases = mutableListOf()
        }
    }

    fun addPurchase(purchase: Purchase): Int {
        val totalIndex: Int
        val purchaseDate = purchase.getLocalDate()
        val totalFound: Boolean
        var i = 0
        if (purchaseList.size != 0) {
            var iDate = purchaseList[i].getLocalDate()
            while (i < purchaseList.size && ChronoUnit.DAYS.between(purchaseDate, iDate) > 0) {
                i++
                iDate = purchaseList[i].getLocalDate()
            }
            totalFound = purchaseList[i].getDateString() == purchase.getDateString()
        } else {
            totalFound = false
        }

        if (totalFound) {
            // Aggiorna totale
            val total = Purchase(
                name = DbPurchases.NAMES.TOTAL.value,
                price = purchaseList[i].price!! + purchase.price!!,
                year = purchaseList[i].year,
                month = purchaseList[i].month,
                day = purchaseList[i].day,
                category = DbPurchases.CATEGORIES.TOTAL.value,
                id = purchaseList[i].getTotalId()
            )
            purchaseList[i] = total
            totalIndex = i
            // Scorri per trovare posizione giusta
            i++
            while (i < purchaseList.size) {
                if (purchaseList[i].getDateString() != purchase.getDateString() ||
                    purchaseList[i].price!! < purchase.price
                ) {
                    break
                }
                i++
            }
            purchaseList.add(i, purchase)
        } else {
            // Giorno non esistente, aggiungi totale
            val total = Purchase(
                name = DbPurchases.NAMES.TOTAL.value,
                price = purchase.price,
                year = purchase.year,
                month = purchase.month,
                day = purchase.day,
                category = DbPurchases.CATEGORIES.TOTAL.value,
                id = purchase.getTotalId()
            )
            purchaseList.add(i, total)
            totalIndex = i
            purchaseList.add(i + 1, purchase)
        }
        return totalIndex
    }

    fun deletePurchaseAt(position: Int) {
        val todayDate = LocalDate.now()
        for (i in position - 1 downTo 0) {
            if (purchaseList[i].category == DbPurchases.CATEGORIES.TOTAL.value) {
                val newTotal = Purchase(
                    name = DbPurchases.NAMES.TOTAL.value,
                    price = purchaseList[i].price!! - purchaseList[position].price!!,
                    year = purchaseList[i].year,
                    month = purchaseList[i].month,
                    day = purchaseList[i].day,
                    category = DbPurchases.CATEGORIES.TOTAL.value,
                    id = purchaseList[i].getTotalId()
                )

                purchaseList.removeAt(position)
                if (
                    newTotal.day == todayDate.dayOfMonth
                    && newTotal.month == todayDate.monthValue
                    && newTotal.year == todayDate.year
                    || newTotal.price != 0.0
                ) {
                    purchaseList[i] = newTotal
                } else {
                    purchaseList.removeAt(i)
                }

                break
            }
        }
    }

    fun addIncome(income: Purchase): Int {
        val totalIndex: Int

        val totalFound: Boolean
        var i = 0
        if (incomeList.size != 0) {
            while (i < incomeList.size && incomeList[i].year!! < income.year!!) {
                i++
            }
            totalFound = incomeList[i].year == income.year
        } else {
            totalFound = false
        }

        if (totalFound) {
            // Aggiorna totale
            val total = Purchase(
                name = DbPurchases.NAMES.TOTAL.value,
                price = incomeList[i].price!! + income.price!!,
                year = incomeList[i].year,
                month = incomeList[i].month,
                day = incomeList[i].day,
                category = DbPurchases.CATEGORIES.TOTAL.value,
                id = incomeList[i].getTotalId()
            )
            incomeList[i] = total
            totalIndex = i
            // Scorri per trovare posizione giusta
            i++
            while (i < incomeList.size) {
                if (incomeList[i].year != income.year ||
                    incomeList[i].price!! < income.price
                ) {
                    break
                }
                i++
            }
            incomeList.add(i, income)
        } else {
            // Giorno non esistente, aggiungi totale
            val total = Purchase(
                name = DbPurchases.NAMES.TOTAL.value,
                price = income.price,
                year = income.year,
                month = income.month,
                day = income.day,
                category = DbPurchases.CATEGORIES.TOTAL.value,
                id = income.getTotalId()
            )
            incomeList.add(i, total)
            totalIndex = i
            incomeList.add(i + 1, income)
        }
        return totalIndex
    }
}