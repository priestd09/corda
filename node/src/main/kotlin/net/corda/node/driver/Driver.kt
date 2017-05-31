@file:JvmName("Driver")

package net.corda.node.driver

import com.google.common.net.HostAndPort
import com.google.common.util.concurrent.*
import com.typesafe.config.Config
import com.typesafe.config.ConfigRenderOptions
import net.corda.client.rpc.CordaRPCClient
import net.corda.cordform.CordformContext
import net.corda.cordform.CordformNode
import net.corda.core.*
import net.corda.core.crypto.X509Utilities
import net.corda.core.crypto.appendToCommonName
import net.corda.core.crypto.commonName
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.ShutdownHook
import net.corda.core.internal.addShutdownHook
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.NodeInfo
import net.corda.core.node.VersionInfo
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.ServiceType
import net.corda.core.utilities.*
import net.corda.node.LOGS_DIRECTORY_NAME
import net.corda.node.services.config.*
import net.corda.node.internal.Node
import net.corda.node.services.config.ConfigHelper
import net.corda.node.services.config.FullNodeConfiguration
import net.corda.node.services.config.VerifierType
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.transactions.RaftValidatingNotaryService
import net.corda.node.utilities.ServiceIdentityGenerator
import net.corda.nodeapi.ArtemisMessagingComponent
import net.corda.nodeapi.User
import net.corda.nodeapi.config.SSLConfiguration
import net.corda.nodeapi.config.parseAs
import okhttp3.OkHttpClient
import okhttp3.Request
import org.bouncycastle.asn1.x500.X500Name
import org.slf4j.Logger
import java.io.File
import java.io.File.pathSeparator
import java.net.*
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread


/**
 * This file defines a small "Driver" DSL for starting up nodes that is only intended for development, demos and tests.
 *
 * The process the driver is run in behaves as an Artemis client and starts up other processes. Namely it first
 * bootstraps a network map service to allow the specified nodes to connect to, then starts up the actual nodes.
 */

private val log: Logger = loggerFor<DriverDSL>()

/**
 * This is the interface that's exposed to DSL users.
 */
interface DriverDSLExposedInterface : CordformContext {
    /**
     * Starts a [net.corda.node.internal.Node] in a separate process.
     *
     * @param providedName Optional name of the node, which will be its legal name in [Party]. Defaults to something
     *     random. Note that this must be unique as the driver uses it as a primary key!
     * @param advertisedServices The set of services to be advertised by the node. Defaults to empty set.
     * @param verifierType The type of transaction verifier to use. See: [VerifierType]
     * @param rpcUsers List of users who are authorised to use the RPC system. Defaults to empty list.
     * @param startInSameProcess Determines if the node should be started inside the same process the Driver is running
     *     in. If null the Driver-level value will be used.
     * @return The [NodeInfo] of the started up node retrieved from the network map service.
     */
    fun startNode(providedName: X500Name? = null,
                  advertisedServices: Set<ServiceInfo> = emptySet(),
                  rpcUsers: List<User> = emptyList(),
                  verifierType: VerifierType = VerifierType.InMemory,
                  customOverrides: Map<String, Any?> = emptyMap(),
                  startInSameProcess: Boolean? = null): ListenableFuture<out NodeHandle>

    fun startNodes(
            nodes: List<CordformNode>,
            startInSameProcess: Boolean? = null
    ): List<ListenableFuture<out NodeHandle>>

    /**
     * Starts a distributed notary cluster.
     *
     * @param notaryName The legal name of the advertised distributed notary service.
     * @param clusterSize Number of nodes to create for the cluster.
     * @param type The advertised notary service type. Currently the only supported type is [RaftValidatingNotaryService.type].
     * @param verifierType The type of transaction verifier to use. See: [VerifierType]
     * @param rpcUsers List of users who are authorised to use the RPC system. Defaults to empty list.
     * @param startInSameProcess Determines if the node should be started inside the same process the Driver is running
     *     in. If null the Driver-level value will be used.
     * @return The [Party] identity of the distributed notary service, and the [NodeInfo]s of the notaries in the cluster.
     */
    fun startNotaryCluster(
            notaryName: X500Name,
            clusterSize: Int = 3,
            type: ServiceType = RaftValidatingNotaryService.type,
            verifierType: VerifierType = VerifierType.InMemory,
            rpcUsers: List<User> = emptyList(),
            startInSameProcess: Boolean? = null): ListenableFuture<Pair<PartyAndCertificate, List<NodeHandle>>>

    /**
     * Starts a web server for a node
     *
     * @param handle The handle for the node that this webserver connects to via RPC.
     */
    fun startWebserver(handle: NodeHandle): ListenableFuture<WebserverHandle>

    /**
     * Starts a network map service node. Note that only a single one should ever be running, so you will probably want
     * to set networkMapStartStrategy to Dedicated(false) in your [driver] call.
     * @param startInProcess Determines if the node should be started inside this process. If null the Driver-level
     *     value will be used.
     */
    fun startDedicatedNetworkMapService(startInProcess: Boolean? = null): ListenableFuture<out NodeHandle>

    fun waitForAllNodesToFinish()

    /**
     * Polls a function until it returns a non-null value. Note that there is no timeout on the polling.
     *
     * @param pollName A description of what is being polled.
     * @param pollInterval The interval of polling.
     * @param warnCount The number of polls after the Driver gives a warning.
     * @param check The function being polled.
     * @return A future that completes with the non-null value [check] has returned.
     */
    fun <A> pollUntilNonNull(pollName: String, pollInterval: Duration = 500.millis, warnCount: Int = 120, check: () -> A?): ListenableFuture<A>
    /**
     * Polls the given function until it returns true.
     * @see pollUntilNonNull
     */
    fun pollUntilTrue(pollName: String, pollInterval: Duration = 500.millis, warnCount: Int = 120, check: () -> Boolean): ListenableFuture<Unit> {
        return pollUntilNonNull(pollName, pollInterval, warnCount) { if (check()) Unit else null }
    }

    val shutdownManager: ShutdownManager
}

interface DriverDSLInternalInterface : DriverDSLExposedInterface {
    fun start()
    fun shutdown()
}

sealed class NodeHandle {
    abstract val nodeInfo: NodeInfo
    abstract val rpc: CordaRPCOps
    abstract val configuration: FullNodeConfiguration
    abstract val webAddress: HostAndPort

    data class OutOfProcess(
            override val nodeInfo: NodeInfo,
            override val rpc: CordaRPCOps,
            override val configuration: FullNodeConfiguration,
            override val webAddress: HostAndPort,
            val debugPort: Int?,
            val process: Process
    ) : NodeHandle()

    data class InProcess(
            override val nodeInfo: NodeInfo,
            override val rpc: CordaRPCOps,
            override val configuration: FullNodeConfiguration,
            override val webAddress: HostAndPort,
            val node: Node,
            val nodeThread: Thread
    ) : NodeHandle()

    fun rpcClientToNode(): CordaRPCClient = CordaRPCClient(configuration.rpcAddress!!)
}

data class WebserverHandle(
        val listenAddress: HostAndPort,
        val process: Process
)

sealed class PortAllocation {
    abstract fun nextPort(): Int
    fun nextHostAndPort(): HostAndPort = HostAndPort.fromParts("localhost", nextPort())

    class Incremental(startingPort: Int) : PortAllocation() {
        val portCounter = AtomicInteger(startingPort)
        override fun nextPort() = portCounter.andIncrement
    }

    object RandomFree : PortAllocation() {
        override fun nextPort(): Int {
            return ServerSocket().use {
                it.bind(InetSocketAddress(0))
                it.localPort
            }
        }
    }
}

/**
 * [driver] allows one to start up nodes like this:
 *   driver {
 *     val noService = startNode(DUMMY_BANK_A.name)
 *     val notary = startNode(DUMMY_NOTARY.name)
 *
 *     (...)
 *   }
 *
 * Note that [DriverDSL.startNode] does not wait for the node to start up synchronously, but rather returns a [Future]
 * of the [NodeInfo] that may be waited on, which completes when the new node registered with the network map service.
 *
 * The driver implicitly bootstraps a [NetworkMapService].
 *
 * @param isDebug Indicates whether the spawned nodes should start in jdwt debug mode and have debug level logging.
 * @param driverDirectory The base directory node directories go into, defaults to "build/<timestamp>/". The node
 * @param portAllocation The port allocation strategy to use for the messaging and the web server addresses. Defaults to incremental.
 * @param debugPortAllocation The port allocation strategy to use for jvm debugging. Defaults to incremental.
 * @param systemProperties A Map of extra system properties which will be given to each new node. Defaults to empty.
 * @param useTestClock If true the test clock will be used in Node.
 * @param networkMapStartStrategy Determines whether a network map node is started automatically.
 * @param startNodesInProcess Provides the default behaviour of whether new nodes should start inside this process or
 *     not. Note that this may be overridden in [DriverDSLExposedInterface.startNode].
 * @param dsl The dsl itself.
 * @return The value returned in the [dsl] closure.
 */
@JvmOverloads
fun <A> driver(
        isDebug: Boolean = false,
        driverDirectory: Path = Paths.get("build", getTimestampAsDirectoryName()),
        portAllocation: PortAllocation = PortAllocation.Incremental(10000),
        debugPortAllocation: PortAllocation = PortAllocation.Incremental(5005),
        systemProperties: Map<String, String> = emptyMap(),
        useTestClock: Boolean = false,
        networkMapStartStrategy: NetworkMapStartStrategy = NetworkMapStartStrategy.Dedicated(startAutomatically = true),
        startNodesInProcess: Boolean = false,
        dsl: DriverDSLExposedInterface.() -> A
) = genericDriver(
        driverDsl = DriverDSL(
                portAllocation = portAllocation,
                debugPortAllocation = debugPortAllocation,
                systemProperties = systemProperties,
                driverDirectory = driverDirectory.toAbsolutePath(),
                useTestClock = useTestClock,
                networkMapStartStrategy = networkMapStartStrategy,
                startNodesInProcess = startNodesInProcess,
                isDebug = isDebug
        ),
        coerce = { it },
        dsl = dsl
)

/**
 * This is a helper method to allow extending of the DSL, along the lines of
 *   interface SomeOtherExposedDSLInterface : DriverDSLExposedInterface
 *   interface SomeOtherInternalDSLInterface : DriverDSLInternalInterface, SomeOtherExposedDSLInterface
 *   class SomeOtherDSL(val driverDSL : DriverDSL) : DriverDSLInternalInterface by driverDSL, SomeOtherInternalDSLInterface
 *
 * @param coerce We need this explicit coercion witness because we can't put an extra DI : D bound in a `where` clause.
 */
fun <DI : DriverDSLExposedInterface, D : DriverDSLInternalInterface, A> genericDriver(
        driverDsl: D,
        coerce: (D) -> DI,
        dsl: DI.() -> A
): A {
    var shutdownHook: ShutdownHook? = null
    try {
        driverDsl.start()
        shutdownHook = addShutdownHook {
            driverDsl.shutdown()
        }
        return dsl(coerce(driverDsl))
    } catch (exception: Throwable) {
        log.error("Driver shutting down because of exception", exception)
        throw exception
    } finally {
        driverDsl.shutdown()
        shutdownHook?.cancel()
    }
}

fun getTimestampAsDirectoryName(): String {
    return DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(UTC).format(Instant.now())
}

class ListenProcessDeathException(message: String) : Exception(message)

/**
 * @throws ListenProcessDeathException if [listenProcess] dies before the check succeeds, i.e. the check can't succeed as intended.
 */
fun addressMustBeBound(executorService: ScheduledExecutorService, hostAndPort: HostAndPort, listenProcess: Process? = null): ListenableFuture<Unit> {
    return poll(executorService, "address $hostAndPort to bind") {
        if (listenProcess != null && !listenProcess.isAlive) {
            throw ListenProcessDeathException("The process that was expected to listen on $hostAndPort has died with status: ${listenProcess.exitValue()}")
        }
        try {
            Socket(hostAndPort.host, hostAndPort.port).close()
            Unit
        } catch (_exception: SocketException) {
            null
        }
    }
}

fun addressMustNotBeBound(executorService: ScheduledExecutorService, hostAndPort: HostAndPort): ListenableFuture<Unit> {
    return poll(executorService, "address $hostAndPort to unbind") {
        try {
            Socket(hostAndPort.host, hostAndPort.port).close()
            null
        } catch (_exception: SocketException) {
            Unit
        }
    }
}

fun <A> poll(
        executorService: ScheduledExecutorService,
        pollName: String,
        pollInterval: Duration = 500.millis,
        warnCount: Int = 120,
        check: () -> A?
): ListenableFuture<A> {
    val initialResult = check()
    val resultFuture = SettableFuture.create<A>()
    if (initialResult != null) {
        resultFuture.set(initialResult)
        return resultFuture
    }
    var counter = 0
    fun schedulePoll() {
        executorService.schedule(task@ {
            counter++
            if (counter == warnCount) {
                log.warn("Been polling $pollName for ${pollInterval.seconds * warnCount} seconds...")
            }
            val result = try {
                check()
            } catch (t: Throwable) {
                resultFuture.setException(t)
                return@task
            }
            if (result == null) {
                schedulePoll()
            } else {
                resultFuture.set(result)
            }
        }, pollInterval.toMillis(), MILLISECONDS)
    }
    schedulePoll()
    return resultFuture
}

class ShutdownManager(private val executorService: ExecutorService) {
    private class State {
        val registeredShutdowns = ArrayList<ListenableFuture<() -> Unit>>()
        var isShutdown = false
    }

    private val state = ThreadBox(State())

    companion object {
        inline fun <A> run(providedExecutorService: ExecutorService? = null, block: ShutdownManager.() -> A): A {
            val executorService = providedExecutorService ?: Executors.newScheduledThreadPool(1)
            val shutdownManager = ShutdownManager(executorService)
            try {
                return block(shutdownManager)
            } finally {
                shutdownManager.shutdown()
                providedExecutorService ?: executorService.shutdown()
            }
        }
    }

    fun shutdown() {
        val shutdownFutures = state.locked {
            if (isShutdown) {
                emptyList<ListenableFuture<() -> Unit>>()
            } else {
                isShutdown = true
                registeredShutdowns
            }
        }
        val shutdowns = shutdownFutures.map { ErrorOr.catch { it.get(1, SECONDS) } }
        shutdowns.reversed().forEach { errorOrShutdown ->
            errorOrShutdown.match(
                    onValue = { shutdown ->
                        try {
                            shutdown()
                        } catch (throwable: Throwable) {
                            log.error("Exception while shutting down", throwable)
                        }
                    },
                    onError = { error ->
                        log.error("Exception while getting shutdown method, disregarding", error)
                    }
            )
        }
    }

    fun registerShutdown(shutdown: ListenableFuture<() -> Unit>) {
        state.locked {
            require(!isShutdown)
            registeredShutdowns.add(shutdown)
        }
    }
    fun registerShutdown(shutdown: () -> Unit) = registerShutdown(Futures.immediateFuture(shutdown))

    fun registerProcessShutdown(processFuture: ListenableFuture<Process>) {
        val processShutdown = processFuture.map { process ->
            {
                process.destroy()
                /** Wait 5 seconds, then [Process.destroyForcibly] */
                val finishedFuture = executorService.submit {
                    process.waitFor()
                }
                try {
                    finishedFuture.get(5, SECONDS)
                } catch (exception: TimeoutException) {
                    finishedFuture.cancel(true)
                    process.destroyForcibly()
                }
                Unit
            }
        }
        registerShutdown(processShutdown)
    }

    interface Follower {
        fun unfollow()
        fun shutdown()
    }

    fun follower() = object : Follower {
        private val start = state.locked { registeredShutdowns.size }
        private val end = AtomicInteger(start - 1)
        override fun unfollow() = end.set(state.locked { registeredShutdowns.size })
        override fun shutdown() = end.get().let { end ->
            start > end && throw IllegalStateException("You haven't called unfollow.")
            state.locked {
                registeredShutdowns.subList(start, end).listIterator(end - start).run {
                    while (hasPrevious()) {
                        previous().getOrThrow().invoke()
                        set(Futures.immediateFuture {}) // Don't break other followers by doing a remove.
                    }
                }
            }
        }
    }
}

class DriverDSL(
        val portAllocation: PortAllocation,
        val debugPortAllocation: PortAllocation,
        val systemProperties: Map<String, String>,
        val driverDirectory: Path,
        val useTestClock: Boolean,
        val isDebug: Boolean,
        val networkMapStartStrategy: NetworkMapStartStrategy,
        val startNodesInProcess: Boolean
) : DriverDSLInternalInterface {
    private val dedicatedNetworkMapAddress = portAllocation.nextHostAndPort()
    var _executorService: ListeningScheduledExecutorService? = null
    val executorService get() = _executorService!!
    var _shutdownManager: ShutdownManager? = null
    override val shutdownManager get() = _shutdownManager!!

    class State {
        val processes = ArrayList<ListenableFuture<Process>>()
    }

    private val state = ThreadBox(State())

    //TODO: remove this once we can bundle quasar properly.
    private val quasarJarPath: String by lazy {
        val cl = ClassLoader.getSystemClassLoader()
        val urls = (cl as URLClassLoader).urLs
        val quasarPattern = ".*quasar.*\\.jar$".toRegex()
        val quasarFileUrl = urls.first { quasarPattern.matches(it.path) }
        Paths.get(quasarFileUrl.toURI()).toString()
    }

    fun registerProcess(process: ListenableFuture<Process>) {
        shutdownManager.registerProcessShutdown(process)
        state.locked {
            processes.add(process)
        }
    }

    override fun waitForAllNodesToFinish() = state.locked {
        Futures.allAsList(processes).get().forEach {
            it.waitFor()
        }
    }

    override fun shutdown() {
        _shutdownManager?.shutdown()
        _executorService?.shutdownNow()
    }

    private fun establishRpc(nodeAddress: HostAndPort, sslConfig: SSLConfiguration): ListenableFuture<CordaRPCOps> {
        val client = CordaRPCClient(nodeAddress, sslConfig)
        return poll(executorService, "for RPC connection") {
            try {
                val connection = client.start(ArtemisMessagingComponent.NODE_USER, ArtemisMessagingComponent.NODE_USER)
                shutdownManager.registerShutdown { connection.close() }
                return@poll connection.proxy
            } catch(e: Exception) {
                log.error("Exception $e, Retrying RPC connection at $nodeAddress")
                null
            }
        }
    }

    private fun networkMapServiceConfigLookup(networkMapCandidates: List<CordformNode>): (X500Name) -> Map<String, String>? {
        return networkMapStartStrategy.run {
            when (this) {
                is NetworkMapStartStrategy.Dedicated -> {
                    serviceConfig(dedicatedNetworkMapAddress).let {
                        { _: X500Name -> it }
                    }
                }
                is NetworkMapStartStrategy.Nominated -> {
                    serviceConfig(HostAndPort.fromString(networkMapCandidates.filter {
                        it.name == legalName.toString()
                    }.single().config.getString("p2pAddress"))).let {
                        { nodeName: X500Name -> if (nodeName == legalName) null else it }
                    }
                }
            }
        }
    }

    override fun startNode(
            providedName: X500Name?,
            advertisedServices: Set<ServiceInfo>,
            rpcUsers: List<User>,
            verifierType: VerifierType,
            customOverrides: Map<String, Any?>,
            startInSameProcess: Boolean?
    ): ListenableFuture<out NodeHandle> {
        val p2pAddress = portAllocation.nextHostAndPort()
        val rpcAddress = portAllocation.nextHostAndPort()
        val webAddress = portAllocation.nextHostAndPort()
        // TODO: Derive name from the full picked name, don't just wrap the common name
        val name = providedName ?: X509Utilities.getDevX509Name("${oneOf(names).commonName}-${p2pAddress.port}")
        val config = ConfigHelper.loadConfig(
                baseDirectory = baseDirectory(name),
                allowMissingConfig = true,
                configOverrides = configOf(
                        "myLegalName" to name.toString(),
                        "p2pAddress" to p2pAddress.toString(),
                        "rpcAddress" to rpcAddress.toString(),
                        "webAddress" to webAddress.toString(),
                        "extraAdvertisedServiceIds" to advertisedServices.map { it.toString() },
                        "networkMapService" to networkMapServiceConfigLookup(emptyList())(name),
                        "useTestClock" to useTestClock,
                        "rpcUsers" to rpcUsers.map { it.toMap() },
                        "verifierType" to verifierType.name
                ) + customOverrides
        )
        return startNodeInternal(config, webAddress, startInSameProcess)
    }

    override fun startNodes(nodes: List<CordformNode>, startInSameProcess: Boolean?): List<ListenableFuture<out NodeHandle>> {
        val networkMapServiceConfigLookup = networkMapServiceConfigLookup(nodes)
        return nodes.map { node ->
            portAllocation.nextHostAndPort() // rpcAddress
            val webAddress = portAllocation.nextHostAndPort()
            val name = X500Name(node.name)

            val config = ConfigHelper.loadConfig(
                    baseDirectory = baseDirectory(name),
                    allowMissingConfig = true,
                    configOverrides = node.config + mapOf(
                            "extraAdvertisedServiceIds" to node.advertisedServices,
                            "networkMapService" to networkMapServiceConfigLookup(name),
                            "rpcUsers" to node.rpcUsers,
                            "notaryClusterAddresses" to node.notaryClusterAddresses
                    )
            )
            startNodeInternal(config, webAddress, startInSameProcess)
        }
    }

    override fun startNotaryCluster(
            notaryName: X500Name,
            clusterSize: Int,
            type: ServiceType,
            verifierType: VerifierType,
            rpcUsers: List<User>,
            startInSameProcess: Boolean?
    ): ListenableFuture<Pair<PartyAndCertificate, List<NodeHandle>>> {
        val nodeNames = (0 until clusterSize).map { DUMMY_NOTARY.name.appendToCommonName(" $it") }
        val paths = nodeNames.map { baseDirectory(it) }
        ServiceIdentityGenerator.generateToDisk(paths, DUMMY_CA, type.id, notaryName)
        val advertisedServices = setOf(ServiceInfo(type, notaryName))
        val notaryClusterAddress = portAllocation.nextHostAndPort()

        // Start the first node that will bootstrap the cluster
        val firstNotaryFuture = startNode(
                providedName = nodeNames.first(),
                advertisedServices = advertisedServices,
                rpcUsers = rpcUsers,
                verifierType = verifierType,
                customOverrides = mapOf("notaryNodeAddress" to notaryClusterAddress.toString()),
                startInSameProcess = startInSameProcess
        )
        // All other nodes will join the cluster
        val restNotaryFutures = nodeNames.drop(1).map {
            val nodeAddress = portAllocation.nextHostAndPort()
            val configOverride = mapOf("notaryNodeAddress" to nodeAddress.toString(), "notaryClusterAddresses" to listOf(notaryClusterAddress.toString()))
            startNode(it, advertisedServices, rpcUsers, verifierType, configOverride)
        }

        return firstNotaryFuture.flatMap { firstNotary ->
            val notaryParty = firstNotary.nodeInfo.notaryIdentity
            Futures.allAsList(restNotaryFutures).map { restNotaries ->
                Pair(notaryParty, listOf(firstNotary) + restNotaries)
            }
        }
    }

    private fun queryWebserver(handle: NodeHandle, process: Process): WebserverHandle {
        val protocol = if (handle.configuration.useHTTPS) "https://" else "http://"
        val url = URL("$protocol${handle.webAddress}/api/status")
        val client = OkHttpClient.Builder().connectTimeout(5, SECONDS).readTimeout(60, SECONDS).build()

        while (process.isAlive) try {
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            if (response.isSuccessful && (response.body().string() == "started")) {
                return WebserverHandle(handle.webAddress, process)
            }
        } catch(e: ConnectException) {
            log.debug("Retrying webserver info at ${handle.webAddress}")
        }

        throw IllegalStateException("Webserver at ${handle.webAddress} has died")
    }

    override fun startWebserver(handle: NodeHandle): ListenableFuture<WebserverHandle> {
        val debugPort = if (isDebug) debugPortAllocation.nextPort() else null
        val processFuture = DriverDSL.startWebserver(executorService, handle, debugPort)
        registerProcess(processFuture)
        return processFuture.map { queryWebserver(handle, it) }
    }

    override fun start() {
        _executorService = MoreExecutors.listeningDecorator(
                Executors.newScheduledThreadPool(2, ThreadFactoryBuilder().setNameFormat("driver-pool-thread-%d").build())
        )
        _shutdownManager = ShutdownManager(executorService)
        if (networkMapStartStrategy.startDedicated) {
            startDedicatedNetworkMapService()
        }
    }

    override fun baseDirectory(nodeName: X500Name): Path = driverDirectory / nodeName.commonName.replace(WHITESPACE, "")

    override fun startDedicatedNetworkMapService(startInProcess: Boolean?): ListenableFuture<out NodeHandle> {
        val webAddress = portAllocation.nextHostAndPort()
        val networkMapLegalName = networkMapStartStrategy.legalName
        val config = ConfigHelper.loadConfig(
                baseDirectory = baseDirectory(networkMapLegalName),
                allowMissingConfig = true,
                configOverrides = configOf(
                        "myLegalName" to networkMapLegalName.toString(),
                        // TODO: remove the webAddress as NMS doesn't need to run a web server. This will cause all
                        //       node port numbers to be shifted, so all demos and docs need to be updated accordingly.
                        "webAddress" to webAddress.toString(),
                        "p2pAddress" to dedicatedNetworkMapAddress.toString(),
                        "useTestClock" to useTestClock
                )
        )
        return startNodeInternal(config, webAddress, startInProcess)
    }

    private fun startNodeInternal(config: Config, webAddress: HostAndPort, startInProcess: Boolean?): ListenableFuture<out NodeHandle> {
        val nodeConfiguration = config.parseAs<FullNodeConfiguration>()
        if (startInProcess ?: startNodesInProcess) {
            val nodeAndThreadFuture = startInProcessNode(executorService, nodeConfiguration, config)
            shutdownManager.registerShutdown(
                    nodeAndThreadFuture.map { (node, thread) -> {
                        node.stop()
                        thread.interrupt()
                    } }
            )
            return nodeAndThreadFuture.flatMap { (node, thread) ->
                establishRpc(nodeConfiguration.p2pAddress, nodeConfiguration).flatMap { rpc ->
                    rpc.waitUntilRegisteredWithNetworkMap().map {
                        NodeHandle.InProcess(rpc.nodeIdentity(), rpc, nodeConfiguration, webAddress, node, thread)
                    }
                }
            }
        } else {
            val debugPort = if (isDebug) debugPortAllocation.nextPort() else null
            val processFuture = startOutOfProcessNode(executorService, nodeConfiguration, config, quasarJarPath, debugPort, systemProperties)
            registerProcess(processFuture)
            return processFuture.flatMap { process ->
                // We continue to use SSL enabled port for RPC when its for node user.
                establishRpc(nodeConfiguration.p2pAddress, nodeConfiguration).flatMap { rpc ->
                    rpc.waitUntilRegisteredWithNetworkMap().map {
                        NodeHandle.OutOfProcess(rpc.nodeIdentity(), rpc, nodeConfiguration, webAddress, debugPort, process)
                    }
                }
            }
        }
    }

    override fun <A> pollUntilNonNull(pollName: String, pollInterval: Duration, warnCount: Int, check: () -> A?): ListenableFuture<A> {
        val pollFuture = poll(executorService, pollName, pollInterval, warnCount, check)
        shutdownManager.registerShutdown { pollFuture.cancel(true) }
        return pollFuture
    }

    companion object {
        private val names = arrayOf(
                ALICE.name,
                BOB.name,
                DUMMY_BANK_A.name
        )

        private fun <A> oneOf(array: Array<A>) = array[Random().nextInt(array.size)]

        private fun startInProcessNode(
                executorService: ListeningScheduledExecutorService,
                nodeConf: FullNodeConfiguration,
                config: Config
        ): ListenableFuture<Pair<Node, Thread>> {
            return executorService.submit<Pair<Node, Thread>> {
                log.info("Starting in-process Node ${nodeConf.myLegalName.commonName}")
                // Write node.conf
                writeConfig(nodeConf.baseDirectory, "node.conf", config)
                // TODO pass the version in?
                val node = nodeConf.createNode(VersionInfo(1, "1", "1", "-"))
                node.start()
                val nodeThread = thread(name = nodeConf.myLegalName.commonName) {
                    node.run()
                }
                node to nodeThread
            }.flatMap { nodeAndThread -> addressMustBeBound(executorService, nodeConf.p2pAddress).map { nodeAndThread } }
        }

        private fun startOutOfProcessNode(
                executorService: ListeningScheduledExecutorService,
                nodeConf: FullNodeConfiguration,
                config: Config,
                quasarJarPath: String,
                debugPort: Int?,
                overriddenSystemProperties: Map<String, String>
        ): ListenableFuture<Process> {
            return executorService.submit<Process> {
                log.info("Starting out-of-process Node ${nodeConf.myLegalName.commonName}")
                // Write node.conf
                writeConfig(nodeConf.baseDirectory, "node.conf", config)

                val systemProperties = overriddenSystemProperties + mapOf(
                        "name" to nodeConf.myLegalName,
                        "visualvm.display.name" to "corda-${nodeConf.myLegalName}",
                        "java.io.tmpdir" to System.getProperty("java.io.tmpdir") // Inherit from parent process
                )
                // TODO Add this once we upgrade to quasar 0.7.8, this causes startup time to halve.
                // val excludePattern = x(rx**;io**;kotlin**;jdk**;reflectasm**;groovyjarjarasm**;groovy**;joptsimple**;groovyjarjarantlr**;javassist**;com.fasterxml**;com.typesafe**;com.google**;com.zaxxer**;com.jcabi**;com.codahale**;com.esotericsoftware**;de.javakaffee**;org.objectweb**;org.slf4j**;org.w3c**;org.codehaus**;org.h2**;org.crsh**;org.fusesource**;org.hibernate**;org.dom4j**;org.bouncycastle**;org.apache**;org.objenesis**;org.jboss**;org.xml**;org.jcp**;org.jetbrains**;org.yaml**;co.paralleluniverse**;net.i2p**)"
                // val extraJvmArguments = systemProperties.map { "-D${it.key}=${it.value}" } +
                //        "-javaagent:$quasarJarPath=$excludePattern"
                val extraJvmArguments = systemProperties.map { "-D${it.key}=${it.value}" } +
                        "-javaagent:$quasarJarPath"
                val loggingLevel = if (debugPort == null) "INFO" else "DEBUG"

                val pluginsDirectory = nodeConf.baseDirectory / "plugins"

                ProcessUtilities.startJavaProcess(
                        className = "net.corda.node.Corda", // cannot directly get class for this, so just use string
                        arguments = listOf(
                                "--base-directory=${nodeConf.baseDirectory}",
                                "--logging-level=$loggingLevel",
                                "--no-local-shell"
                        ),
                        // Like the capsule, include the node's plugin directory
                        classpath = "${ProcessUtilities.defaultClassPath}$pathSeparator$pluginsDirectory/*",
                        jdwpPort = debugPort,
                        extraJvmArguments = extraJvmArguments,
                        errorLogPath = nodeConf.baseDirectory / LOGS_DIRECTORY_NAME / "error.log",
                        workingDirectory = nodeConf.baseDirectory
                )
            }.flatMap { process -> addressMustBeBound(executorService, nodeConf.p2pAddress, process).map { process } }
        }

        private fun startWebserver(
                executorService: ListeningScheduledExecutorService,
                handle: NodeHandle,
                debugPort: Int?
        ): ListenableFuture<Process> {
            return executorService.submit<Process> {
                val className = "net.corda.webserver.WebServer"
                ProcessUtilities.startJavaProcess(
                        className = className, // cannot directly get class for this, so just use string
                        arguments = listOf("--base-directory", handle.configuration.baseDirectory.toString()),
                        jdwpPort = debugPort,
                        extraJvmArguments = listOf(
                            "-Dname=node-${handle.configuration.p2pAddress}-webserver",
                            "-Djava.io.tmpdir=${System.getProperty("java.io.tmpdir")}" // Inherit from parent process
                        ),
                        errorLogPath = Paths.get("error.$className.log")
                )
            }.flatMap { process -> addressMustBeBound(executorService, handle.webAddress, process).map { process } }
        }
    }
}

fun writeConfig(path: Path, filename: String, config: Config) {
    path.toFile().mkdirs()
    File("$path/$filename").writeText(config.root().render(ConfigRenderOptions.defaults()))
}

