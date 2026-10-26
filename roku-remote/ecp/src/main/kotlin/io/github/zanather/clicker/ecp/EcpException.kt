package io.github.zanather.clicker.ecp

/** Every failure [EcpClient] can raise. Callers branch on the subtype to pick a recovery. */
sealed class EcpException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** No TCP connection: wrong IP, TV in deep sleep, different network, or a VPN swallowing LAN traffic. */
    class Unreachable(val address: RokuAddress, cause: Throwable) :
        EcpException("Can't reach $address: ${cause.message}", cause)

    /** Connected but no answer in time. Usually a TV waking up or a congested network. */
    class Timeout(val address: RokuAddress, cause: Throwable) :
        EcpException("$address didn't answer in time", cause)

    /**
     * HTTP 403. When [limitedMode] is true the body was Roku's exact
     * `ECP command not allowed in Limited mode.`: the TV's "Control by mobile apps > Network access" is on
     * Limited and must be switched to Enabled. Otherwise access is Disabled or our source IP isn't allowed.
     */
    class Forbidden(val address: RokuAddress, val path: String, val limitedMode: Boolean) :
        EcpException(
            if (limitedMode) "$address is in Limited mode and refused $path" else "$address refused $path",
        )

    /** Any other non-2xx status. */
    class Http(val address: RokuAddress, val path: String, val code: Int) :
        EcpException("$address answered $code to $path")

    /** A 2xx response we couldn't parse. */
    class BadResponse(val address: RokuAddress, val path: String, cause: Throwable? = null) :
        EcpException("$address sent an unreadable response to $path", cause)
}
