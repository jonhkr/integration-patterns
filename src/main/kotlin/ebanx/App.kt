package ebanx

import java.io.IOException
import java.util.Collections.emptyIterator
import java.util.Collections.unmodifiableMap
import java.util.UUID
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun main() {
    Connections.init(
        ConnectionBehaviour.SUCCEED,
        ConnectionBehaviour.FAIL_BEFORE,
        ConnectionBehaviour.SUCCEED,
        ConnectionBehaviour.SUCCEED
    )

    val log = logger("main")
    val ledger = Ledger()
    val provider = PaymentProvider()
    val service = PaymentService(ledger, provider, PaymentRepository())

    val key = UUID.randomUUID().toString()

    val repeatTimes = 2

    repeat(repeatTimes) {
        log.info("Execution {} of {}", it + 1, repeatTimes)
        try {
            service.createPayment(PaymentRequest(key, 100))
        } catch (e: Exception) {
            log.error("Failed to create payment", e)
        }

        dump(ledger, provider, service)
    }
}

fun dump(vararg dump: Dump) {
    dump.forEach { it.dump() }
}

interface Dump {
    fun dump()
}

fun logger(name: String): Logger = LoggerFactory.getLogger(name)

class OptimisticLockingException : RuntimeException()

abstract class Service<I, O>(name: String) : Dump {
    private val executedRequests = mutableMapOf<I, O>()
    protected val log = logger(name)

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

enum class ConnectionBehaviour {
    FAIL_BEFORE,
    FAIL_AFTER,
    SUCCEED
}

object Connections {
    private var behaviours = emptyIterator<ConnectionBehaviour>()

    fun init(vararg behaviours: ConnectionBehaviour) {
        this.behaviours = behaviours.iterator()
    }

    fun <R> execute(fn: () -> R): R {
        val behaviour = if (behaviours.hasNext()) {
            behaviours.next()
        } else {
            ConnectionBehaviour.SUCCEED
        }

        return Connection(behaviour, fn).execute()
    }
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

// Ledger
data class DebitRequest(val idempotencyKey: String, val accountId: Int, val amount: Int)
data class DebitResponse(val transactionId: String)

class Ledger : Service<DebitRequest, DebitResponse>("ledger") {
    fun debit(debitRequest: DebitRequest): DebitResponse = execute(debitRequest)
    override fun response(request: DebitRequest) = DebitResponse(UUID.randomUUID().toString())
}

// Payment Provider
data class PayRequest(val idempotencyKey: String, val amount: Int)
data class PayResponse(val transactionId: String)

class PaymentProvider : Service<PayRequest, PayResponse>("paymentProvider") {
    fun pay(payRequest: PayRequest): PayResponse = execute(payRequest)
    override fun response(request: PayRequest) = PayResponse(UUID.randomUUID().toString())
}

// Payment repository
data class Payment(val key: String, val amount: Int, val status: PaymentStatus, val version: Int)

class PaymentRepository {
    private val log = logger("paymentRepository")
    private val store = mutableMapOf<String, Payment>()

    fun save(payment: Payment): Payment {
        log.info("Saving payment: {} -> {}", payment.key, payment)
        store.computeIfPresent(payment.key) { _, existing ->

            log.info("Payment with key={} already exists, updating", payment.key)
            if (existing.version >= payment.version) {
                log.error(
                    "Failed to update payment with key={}, the provided payment version is invalid: {} should be > {}",
                    payment.key,
                    payment.version,
                    existing.version
                )

                throw OptimisticLockingException()
            }

            payment
        }

        store.putIfAbsent(payment.key, payment)

        return payment
    }

    fun get(key: String) = store[key]

    fun getAll(): Map<String, Payment> = unmodifiableMap(store)
}

// Payment Service
data class PaymentRequest(val idempotencyKey: String, val amount: Int)
data class PaymentResponse(val id: String)

enum class PaymentStatus {
    PROCESSING,
    PAID,
}

class PaymentService(
    private val ledger: Ledger,
    private val paymentProvider: PaymentProvider,
    private val repo: PaymentRepository
) :
    Service<PaymentRequest, PaymentResponse>("paymentService") {

    fun createPayment(request: PaymentRequest): PaymentResponse = execute(request)

    override fun response(request: PaymentRequest): PaymentResponse {
        val payment = try {
            repo.save(Payment(request.idempotencyKey, request.amount, PaymentStatus.PROCESSING, 0))
        } catch (e: OptimisticLockingException) {
            repo.get(request.idempotencyKey)!!
        }

        if (payment.status == PaymentStatus.PAID) {
            log.info("Payment is already paid")
            return PaymentResponse(payment.key)
        }

        Connections.execute { ledger.debit(DebitRequest(payment.key, 1, payment.amount)) }
        Connections.execute { paymentProvider.pay(PayRequest(payment.key, payment.amount)) }

        repo.save(payment.copy(status = PaymentStatus.PAID, version = payment.version + 1))

        return PaymentResponse(payment.key)
    }

    override fun dump() {
        super.dump()

        log.info("Dumping persisted payments")
        repo.getAll().forEach { (k, v) ->
            log.info("{} -> {}", k, v)
        }
    }
}
