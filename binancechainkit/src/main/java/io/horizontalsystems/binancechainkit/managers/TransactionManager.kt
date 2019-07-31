package io.horizontalsystems.binancechainkit.managers

import io.horizontalsystems.binancechainkit.BinanceChainApiProvider
import io.horizontalsystems.binancechainkit.core.IStorage
import io.horizontalsystems.binancechainkit.models.SyncState
import io.horizontalsystems.binancechainkit.models.Transaction
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.math.BigDecimal
import java.util.*

class TransactionManager(private val storage: IStorage, private val apiProvider: BinanceChainApiProvider) {

    interface Listener {
        fun onSyncTransactions(transactions: List<Transaction>)
    }

    var listener: Listener? = null

    private val disposables = CompositeDisposable()

    // 3 months duration
    private val windowTime: Long = 88 * 24 * 3600 * 1000L
    private val binanceLaunchTime: Long = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { set(2019, 0, 1, 0, 0, 0) }.time.time

    fun getTransactions(symbol: String, fromTransactionHash: String? = null, limit: Int? = null): Single<List<Transaction>> {
        return Single.just(storage.getTransactions(symbol, fromTransactionHash, limit))
    }

    fun sync(account: String) {
        val syncedUntilTime = storage.syncState?.transactionSyncedUntilTime ?: binanceLaunchTime

        syncTransactionsPartially(account, syncedUntilTime)
            .subscribeOn(Schedulers.io())
            .subscribe({}, {
                it.printStackTrace()
            })
            .let { disposables.add(it) }
    }

    private fun syncTransactionsPartially(account: String, startTime: Long): Single<Unit> {
        return apiProvider.getTransactions(account, startTime)
            .flatMap {
                val currentTime = Date().time

                val syncedUntil = when {
                    it.size == 1000 -> it.last().blockTime.time
                    else -> Math.min(startTime + windowTime, currentTime)
                }

                storage.addTransactions(it)
                storage.syncState = SyncState(syncedUntil)

                listener?.onSyncTransactions(it)

                if (syncedUntil < currentTime) {
                    syncTransactionsPartially(account, syncedUntil)
                } else {
                    Single.just(Unit)
                }
            }
    }

    fun stop() {
        disposables.dispose()
    }

    fun send(symbol: String, to: String, amount: BigDecimal, memo: String): Single<String> {
        TODO()
    }

}