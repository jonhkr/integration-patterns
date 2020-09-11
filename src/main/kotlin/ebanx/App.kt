package ebanx

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*

fun main() {
    val ledger = Ledger()
    val provider = PaymentProvider()
    val service = PaymentService(ledger, provider)

    try {
        service.createPayment(PaymentRequest(100))
    } catch (e: Exception) {
        e.printStackTrace()
    }

    dump(ledger, provider, service)
}

fun dump(vararg dump: Dump) {
    dump.forEach { it.dump() }
}

interface Dump {
    fun dump()
}

fun logger(name: String): Logger = LoggerFactory.getLogger(name)

abstract class Service<I, O>(name: String) : Dump {
    private val executedRequests = mutableMapOf<I, O>()
    private val log = logger(name)

    protected fun execute(request: I): O {
        log.info("Executing request: {}", request)
        val resp = response(request)
        executedRequests[request] = resp
        log.info("Sending response: {}", resp)
        return resp
    }

    protected abstract fun response(request: I): O

    override fun dump() {
        log.info("Dumping executed requests")
        executedRequests.forEach { log.info("Request {} -> {}", it.key, it.value) }
    }
}


// Ledger
data class DebitRequest(val accountId: Int, val amount: Int)
data class DebitResponse(val transactionId: String)

class Ledger : Service<DebitRequest, DebitResponse>("ledger") {
    fun debit(debitRequest: DebitRequest): DebitResponse = execute(debitRequest)
    override fun response(request: DebitRequest) = DebitResponse(UUID.randomUUID().toString())
}


// Payment Provider
data class PayRequest(val amount: Int)
data class PayResponse(val transactionId: String)

class PaymentProvider : Service<PayRequest, PayResponse>("paymentProvider") {
    fun pay(payRequest: PayRequest): PayResponse = execute(payRequest)
    override fun response(request: PayRequest) = PayResponse(UUID.randomUUID().toString())
}


// Payment Service
data class PaymentRequest(val amount: Int)
data class PaymentResponse(val id: String)

class PaymentService(private val ledger: Ledger, private val paymentProvider: PaymentProvider)
    : Service<PaymentRequest, PaymentResponse>("paymentService") {

    fun createPayment(request: PaymentRequest): PaymentResponse = execute(request)

    override fun response(request: PaymentRequest): PaymentResponse {
        ledger.debit(DebitRequest(1, request.amount))
        paymentProvider.pay(PayRequest(request.amount))
        return PaymentResponse(UUID.randomUUID().toString())
    }
}