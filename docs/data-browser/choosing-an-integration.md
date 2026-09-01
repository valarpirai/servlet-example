# Choosing an Integration

Three questions decide how to connect to something new. Ask them in order.

## 1. Does it have a JDBC driver?

If yes, it is not a new pattern. Implement `DataSourceStrategy`, add a line to
the services file, done. See
[DataSource strategies](./datasource-strategies.md).

This covers most relational and relational-ish stores with a driver on Maven
Central.

## 2. Is the wire protocol plain HTTP?

If the target speaks REST over HTTPS, talk to it directly. `java.net.http.HttpClient`
is in the JDK. So are `javax.crypto.Mac` and `java.security.MessageDigest`, which
cover HMAC request signing schemes.

Nothing needs downloading, loading or reflecting. No dependency, no
`DriverShim`, no `URLClassLoader`.

Check this before reaching for reflection. Object stores and most cloud APIs
land here.

## 3. Does it need a real client library?

Only if the protocol is proprietary. MongoDB is the case in point: a binary wire
protocol, so either the vendor driver or reimplementing the protocol by hand.

Then choose how the library arrives.

| | Compile-time dependency | Reflection-loaded |
|---|---|---|
| Build files | New managed dependency and catalog entry | None |
| Availability | Always on the classpath | Downloaded to `data/extlib/` on first use |
| Calling code | Direct and fully typed | `Class.forName` plus `Method.invoke` |
| Matches existing UX | No, the driver ships with the app | Yes, download on demand |

The established convention here is reflection-loaded, matching the
download-on-demand experience users already get for JDBC. See
[MongoDB mode](./mongodb-mode.md).

Pick a compile-time dependency when the library is small and stable and the call
surface is wide enough that reflection would dominate the code.

## Look for a shared JDK interface first

Before writing reflection for a whole API, check whether any part of it
implements a JDK type. Those can be cast normally across classloaders.

`java.sql.Driver` is what makes `DriverShim` possible.
`java.util.Iterator` is what lets a Mongo cursor be driven without reflection.
Finding one of these shrinks the reflective surface sharply.

## Related

- [DataSource strategies](./datasource-strategies.md) — the JDBC path
- [Driver loading](./driver-loading.md) — the shim mechanism
- [MongoDB mode](./mongodb-mode.md) — the reflection path
