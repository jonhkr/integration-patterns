package ebanx

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*

interface Dump {
    fun dump()
}

fun logger(name: String): Logger = LoggerFactory.getLogger(name)

enum class ConnectionBehaviour {
    FAIL_BEFORE,
    FAIL_AFTER,
    SUCCEED
}

class Connection<R>(private val behaviour: ConnectionBehaviour, val fn: () -> R) {
    fun execute(): R = when (behaviour) {
        ConnectionBehaviour.FAIL_BEFORE -> throw IOException("Failed before")
        ConnectionBehaviour.FAIL_AFTER -> {
            fn()
            throw IOException("Failed after")
        }
        ConnectionBehaviour.SUCCEED -> fn()
    }
}

abstract class Service<I, O>(name: String) : Dump {
    private val executedRequests = mutableMapOf<I, O>()
    private val log = logger(name)

    protected fun execute(request: I): O {
        log.info("Executing request: {}", request)
        val resp = response()
        log.info("Sending response: {}", resp)
        return resp
    }

    protected abstract fun response(): O

    override fun dump() {
        log.info("Dumping executed requests")
        executedRequests.forEach { log.info("Request {} -> {}", it.key, it.value) }
    }
}

data class DebitRequest(val accountId: Int, val amount: Int)
data class DebitResponse(val transactionId: String)


class Ledger : Service<DebitRequest, DebitResponse>("ledger") {
    fun debit(debitRequest: DebitRequest): DebitResponse = execute(debitRequest)
    override fun response() = DebitResponse(UUID.randomUUID().toString())
}

data class PayRequest(val amount: Int)
data class PayResponse(val transactionId: String)

class PaymentProvider : Service<PayRequest, PayResponse>("paymentProvider") {
    fun pay(payRequest: PayRequest): PayResponse = execute(payRequest)
    override fun response() = PayResponse(UUID.randomUUID().toString())
}

data class PaymentRequest(val amount: Int)
data class PaymentResponse(val id: String)

class PaymentService(private val ledger: Ledger, private val paymentProvider: PaymentProvider) : Dump {
    fun createPayment(paymentRequest: PaymentRequest): PaymentResponse {

        Connection(ConnectionBehaviour.FAIL_AFTER) {
            ledger.debit(DebitRequest(1, paymentRequest.amount))
        }.execute()

        Connection(ConnectionBehaviour.SUCCEED) {
            paymentProvider.pay(PayRequest(paymentRequest.amount))
        }.execute()

        return PaymentResponse(UUID.randomUUID().toString())
    }

    override fun dump() {

    }
}

fun main() {
    val ledger = Ledger()
    val provider = PaymentProvider()
    val service = PaymentService(ledger, provider)

    try {
        service.createPayment(PaymentRequest(100))
    } catch (e: Exception) {
        dump(ledger, provider, service)
    }
}

fun dump(vararg dump: Dump) {
    dump.forEach { it.dump() }
}