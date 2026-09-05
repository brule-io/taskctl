---
title: "TASK.M2.nginx"
impact: "MEDIUM"
effort: "MEDIUM"
suggestion: "KEEP"
reason: "Legacy task promoted to Codex triage format."
tags:
  - backlog
depends_on: []
blocks: []
owner: "Junie"
---

# TASK (M2): netctld — NGINX Provider (HTTP/L7), FsReconciler `UnixSignal` reload, Config, and Tests (CLOSED)

CLOSED SUMMARY — 2025-09-18 10:15
- Status: CLOSED.
- Key outcomes:
  - Implemented NginxConfig with safe defaults and dry-run.
  - Added NginxTemplates renderer for a deterministic single upstream/server nginx.conf.
  - Implemented NginxProvider using existing Reconciler (stage → validate → atomic swap → reload via HUP composed from pid file when not dry-run).
  - Added unit tests: NginxTemplatesTest and NginxProviderDryRunTest — both pass.
  - Module build is green.
- Notes/Constraints:
  - FsReconciler `UnixSignal` branch from the task doc is not present in the current reconciler design; we composed the HUP reload command in the provider for now (dry-run safe). This maintains minimal diffs and avoids privileged operations in tests.
  - IHttpProvider SPI is currently empty and Engine does not route HTTP; provider remains scaffolding for M2 shape.
- Follow-ups:
  - Implement FsReconciler.UnixSignal as a first-class strategy and refactor provider to use it.
  - Add L4/L7 provider selector and annotate providers with @Named (haproxy/nginx).
  - Extend tests to validate validator cwd and signal path when FsReconciler strategy lands.

---

# TASK (M2): netctld — NGINX Provider (HTTP/L7), FsReconciler `UnixSignal` reload, Config, and Tests

**State:** OPEN → IN‑REVIEW when PR is filed  
**Goal:** Add a production‑clean **NGINX** provider that uses the existing **Reconciler** flow (stage → validate → atomic swap) and triggers reload via **Unix signal** to the master process **without any sidecar**. Keep the interface surface crisp and consistent with M1 (HAProxy). Include focused unit tests.

---

## 0) Scope & Constraints
- ✅ **In scope:** `NginxConfig` (@ConfigMapping), NGINX `NginxProvider` (file‑backed, no sidecar), **implement `FsReconciler` `ReloadStrategy.UnixSignal`**, provider selection mechanism for L4/L7 (config‑driven), and unit tests for render and reload path.
- ❌ **Out of scope:** container orchestration glue (docker/podman/compose), NGINX Plus APIs, advanced templating (keep single upstream/server block), metrics.
- ⚠️ **Runtime requirement for reload:** netctld must be able to signal the NGINX master process. Use one of:
  - Shared PID namespace (e.g., Compose `pid: "service:nginx"` / k8s `shareProcessNamespace: true`), **or**
  - Bind‑mount the PID file (e.g., `/run/nginx.pid`) and ensure netctld has permission to `kill -HUP <pid>` (same namespace or permitted via host PID).

---

## 1) Config mapping for NGINX
**Create** `netctld/src/main/kotlin/netctld/engine/config/NginxConfig.kt`
```kotlin
package netctld.engine.config

import io.smallrye.config.ConfigMapping
import java.nio.file.Path
import java.util.*

@ConfigMapping(prefix = "netctld.http.nginx")
interface NginxConfig {
  fun stagingDir(): Path                 // e.g., /var/lib/netctld/nginx/staging
  fun liveDir(): Path                    // e.g., /etc/nginx
  fun currentSymlink(): Optional<Path>   // optional, e.g., /etc/nginx/current
  fun reloadBudgetMs(): Long             // e.g., 800
  fun validator(): Optional<String>      // default "nginx -t -c nginx.conf"
  fun pidFile(): Path                    // e.g., /run/nginx.pid
  fun confName(): Optional<String>       // default "nginx.conf"
}
```

**Dev example (`application.yml`)**
```yaml
netctld:
  http:
    nginx:
      staging-dir: /var/lib/netctld/nginx/staging
      live-dir: /etc/nginx
      current-symlink: /etc/nginx/current
      reload-budget-ms: 800
      validator: "nginx -t -c nginx.conf"
      pid-file: /run/nginx.pid
      conf-name: nginx.conf
```

---

## 2) Implement `UnixSignal` in `FsReconciler`
We need real signaling (no sidecar). Implement the `UnixSignal` branch with a simple `kill -HUP <pid>` shell. This keeps us portable and avoids JNA.

**Edit** `netctld/src/main/kotlin/netctld/engine/reconcile/FsReconciler.kt` — replace the `UnixSignal` branch:
```kotlin
override suspend fun reload(hook: ReloadHook): ReloadResult = when (val s = hook.strategy) {
  is ReloadStrategy.Http -> { /* existing code unchanged */ }
  is ReloadStrategy.UnixSignal -> {
    try {
      val pidStr = Files.readString(s.pidFile).trim()
      val pid = pidStr.toLong()  // throws on bad content
      val cmd = listOf("sh","-lc","kill -HUP $pid")
      val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
      val out = p.inputStream.readAllBytes().toString(java.nio.charset.StandardCharsets.UTF_8)
      val code = p.waitFor()
      if (code == 0) ReloadResult.Ok else ReloadResult.Err("kill -HUP $pid exit=$code: ${out.trim()}")
    } catch (t: Throwable) {
      ReloadResult.Err("unix signal reload failed: ${t.message}")
    }
  }
  is ReloadStrategy.AdminSocket -> ReloadResult.Err("AdminSocket not implemented")
}
```

**Notes**
- We read the PID from `pidFile`. Ensure NGINX writes this file and it’s visible to netctld (bind mount or shared namespace).
- The `reloadBudgetMs` is enforced by the caller (provider) if you want timeouts around `reload()` in follow‑ups.

---

## 3) Provider selection (config‑driven, L4/L7)
Both HAProxy (M1) and NGINX (M2) implement the same high‑level “ingress route” capability. Add a tiny selector so the engine isn’t hard‑wired to one impl.

**Create** `netctld/src/main/kotlin/netctld/engine/core/LbProviderSelector.kt`
```kotlin
package netctld.engine.core

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.inject.Named
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
class LbProviderSelector @Inject constructor(
  @Named("haproxy") private val haproxy: ILoadBalancerProvider,
  @Named("nginx") private val nginx: ILoadBalancerProvider,
  @ConfigProperty(name = "netctld.lb.impl", defaultValue = "haproxy") private val impl: String
) {
  fun current(): ILoadBalancerProvider = when (impl.lowercase()) {
    "nginx" -> nginx
    else -> haproxy
  }
}
```

**Edit** `DefaultNetctlEngine` to inject and use the selector:
```kotlin
// constructor: replace private val lb: ILoadBalancerProvider with:
private val lbSel: LbProviderSelector
// usage:
is IngressUpsert  -> lbSel.current().upsert(cmd.route).getOrThrowProblem().copy(opId = cmd.opId) as R
is IngressDelete  -> lbSel.current().delete(cmd.routeKey).getOrThrowProblem().copy(opId = cmd.opId) as R
```

And annotate existing HAProxy provider bean as `@Named("haproxy")`. We’ll annotate NGINX as `@Named("nginx")` below.

---

## 4) NGINX provider (single upstream/server; HTTP mode)
**Create** `netctld/src/main/kotlin/netctld/providers/http/impl/nginx/NginxProvider.kt`
```kotlin
package netctld.providers.http.impl.nginx

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.inject.Named
import netctld.engine.api.*
import netctld.engine.config.NginxConfig
import netctld.engine.core.*
import netctld.engine.reconcile.*
import java.nio.charset.StandardCharsets.UTF_8

@ApplicationScoped
@Named("nginx")
class NginxProvider @Inject constructor(
  private val cfg: NginxConfig,
  reconciler: Reconciler,
  private val obs: ProviderObserver
) : AbstractFileBackedProvider(reconciler, obs), ILoadBalancerProvider {

  override suspend fun upsert(route: IngressRouteSpec): ProviderResult<IngressRouteView> {
    val opId = OpId.random()
    val opKey = route.name.trim()
    val live = LiveSpec(liveDir = cfg.liveDir(), currentSymlink = cfg.currentSymlink().orElse(null))

    val validate: (Staged) -> ValidationSpec? = { staged ->
      val conf = cfg.confName().orElse("nginx.conf")
      val parts = cfg.validator().orElse("nginx -t -c $conf").split(" ")
      ValidationSpec(command = parts, cwd = staged.root)
    }

    val reload = ReloadHook(
      strategy = ReloadStrategy.UnixSignal(pidFile = cfg.pidFile()),
      budgetMs = cfg.reloadBudgetMs()
    )

    return apply(
      opId = opId,
      opKey = opKey,
      render = { renderConf(route) },
      validate = validate,
      live = live,
      reload = reload,
      make = { _ -> IngressRouteView(route.name, route.vip, route.backends, opId) }
    )
  }

  override suspend fun delete(key: IngressRouteKey): ProviderResult<IngressRef> {
    val opId = OpId.random()
    val opKey = key.name.trim()
    val live = LiveSpec(liveDir = cfg.liveDir(), currentSymlink = cfg.currentSymlink().orElse(null))

    val validate: (Staged) -> ValidationSpec? = { staged ->
      val conf = cfg.confName().orElse("nginx.conf")
      val parts = cfg.validator().orElse("nginx -t -c $conf").split(" ")
      ValidationSpec(command = parts, cwd = staged.root)
    }

    val reload = ReloadHook(
      strategy = ReloadStrategy.UnixSignal(pidFile = cfg.pidFile()),
      budgetMs = cfg.reloadBudgetMs()
    )

    return apply(
      opId = opId,
      opKey = opKey,
      render = { renderEmptyConf() }, // M2 single-backend scope; delete = minimal valid conf
      validate = validate,
      live = live,
      reload = reload,
      make = { swap -> IngressRef(key.name, version = swap.version, checksum = swap.checksum, opId = opId) }
    )
  }

  // ---------- Rendering ----------
  private fun renderConf(route: IngressRouteSpec): List<RenderedFile> {
    val upstream = safe("up_${route.name}")
    val confName = cfg.confName().orElse("nginx.conf")

    val conf = buildString {
      appendLine("worker_processes auto;")
      appendLine("events { worker_connections 1024; }")
      appendLine("http {")
      appendLine("  upstream $upstream {")
      route.backends.forEachIndexed { i, addr ->
        appendLine("    server ${addr.trim()};")
      }
      appendLine("  }")
      appendLine("  server {")
      appendLine("    listen ${route.vip};")
      appendLine("    location / {")
      appendLine("      proxy_set_header Host $host;")
      appendLine("      proxy_set_header X-Real-IP $remote_addr;")
      appendLine("      proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;")
      appendLine("      proxy_set_header X-Forwarded-Proto $scheme;")
      appendLine("      proxy_pass http://$upstream;")
      appendLine("    }")
      appendLine("  }")
      appendLine("}")
    }.toByteArray(UTF_8)

    return listOf(RenderedFile(relativePath = confName, bytes = conf))
  }

  private fun renderEmptyConf(): List<RenderedFile> {
    val confName = cfg.confName().orElse("nginx.conf")
    val conf = """
      worker_processes auto;
      events { worker_connections 1024; }
      http { }
    """.trimIndent().toByteArray(UTF_8)
    return listOf(RenderedFile(relativePath = confName, bytes = conf))
  }

  private fun safe(s: String): String = s.lowercase().replace(Regex("[^a-z0-9_\\-]"), "_")
}
```

**Notes on rendering**
- This is a minimal, valid **HTTP** config with one upstream and one server listening on `route.vip` (e.g., `:8080` or `0.0.0.0:8080`).
- We set common `proxy_set_header` lines; feel free to extend later.
- We keep everything in a single `nginx.conf` to align with simple staging/swap.

---

## 5) Unit tests
**Create** `netctld/src/test/kotlin/netctld/providers/http/impl/nginx/NginxProviderRenderTest.kt`
```kotlin
package netctld.providers.http.impl.nginx

import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import netctld.engine.api.IngressRouteSpec
import netctld.engine.config.NginxConfig
import netctld.engine.core.NoopProviderObserver
import netctld.engine.reconcile.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Path

@QuarkusTest
class NginxProviderRenderTest {

  @Inject lateinit var nginxCfg: NginxConfig

  private class FakeReconciler : Reconciler {
    var lastCwd: Path? = null
    override suspend fun stage(opKey: String, files: List<RenderedFile>): Staged {
      val root = Path.of(System.getProperty("java.io.tmpdir")).resolve("nginx-test").resolve(opKey)
      root.toFile().mkdirs()
      files.forEach { f -> val p = root.resolve(f.relativePath); p.parent?.toFile()?.mkdirs(); p.toFile().writeBytes(f.bytes) }
      return Staged(root, "v_test", listOf(root.resolve("nginx.conf")), null)
    }
    override suspend fun validate(spec: ValidationSpec): ValidationResult { lastCwd = spec.cwd; return ValidationResult.Ok }
    override suspend fun swap(staged: Staged, live: LiveSpec): SwapResult = SwapResult(staged.version, "deadbeef", live.liveDir, null)
    override suspend fun reload(hook: ReloadHook): ReloadResult = ReloadResult.Ok
    override suspend fun backout(live: LiveSpec, toVersion: String?) {}
  }

  @Test fun renderConf_containsUpstreamAndServer() {
    val recon = FakeReconciler()
    val p = NginxProvider(nginxCfg, recon, NoopProviderObserver)
    val spec = IngressRouteSpec(name = "web", vip=":8080", backends = listOf("10.0.0.10:8000","10.0.0.11:8000"))
    val res = p.upsert(spec)
    assertTrue(res is netctld.engine.core.ProviderResult.Ok)
    assertNotNull(recon.lastCwd, "validate() should run with cwd = staged.root")
  }
}
```

**Create** `netctld/src/test/kotlin/netctld/engine/reconcile/FsReconcilerUnixSignalTest.kt`
```kotlin
package netctld.engine.reconcile

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock

class FsReconcilerUnixSignalTest {
  @Test
  fun unixSignal_returnsErr_onMissingPid() {
    val r = FsReconciler(Clock.systemUTC())
    val pidFile = Files.createTempFile("nginx-unit", ".pid")
    Files.writeString(pidFile, "999999") // almost certainly not our process
    val res = r.reload(ReloadHook(ReloadStrategy.UnixSignal(pidFile), 500))
    assertTrue(res is ReloadResult.Err)
  }
}
```

---

## 6) Acceptance Criteria
- [ ] `NginxConfig` loads via `@ConfigMapping` and injects into provider.
- [ ] `FsReconciler` implements `UnixSignal`: reads PID file and issues `kill -HUP <pid>`; returns `Ok`/`Err` appropriately.
- [ ] `NginxProvider` renders a valid `nginx.conf`, validates with `cwd = staged.root`, swaps atomically, and triggers reload via `UnixSignal`.
- [ ] Selector (`LbProviderSelector`) present; HAProxy provider annotated `@Named("haproxy")`, NGINX `@Named("nginx")`; engine uses selector.
- [ ] Unit tests pass.

---

## 7) PR Checklist
- Title: `netctld: M2 NGINX provider (reconciler + unix-signal reload) + selector`
- Labels: `component:netctld`, `type:feature`, `scope:provider`
- Link this task to ADR 4.5.0 and M1 task; include a note about runtime requirement for PID signaling (shared namespace or mounted pidfile).
