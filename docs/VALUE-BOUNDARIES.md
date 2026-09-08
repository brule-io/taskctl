# Stable typed values at construction and mutation boundaries

The red witness at `ac4c46fc63299cabd805e61ebb6fe97601099162` demonstrates
five failing shared regressions: three collection-ownership cases and two invalid
programmatic record-input cases. Completed logs and XML are retained in
`docs/proof/value-boundaries`. A separate Java probe against source
`1759247376107c56a6b9e48af5ef0152a0dc54f8` changed an extension map after a task
constructor validated it. Bootstrap produced a plan whose task could not be
decoded. The probe never applied that plan and the target did not exist.

`ObjectValue` and `ArrayValue` now take owned, unmodifiable copies of their input
collections. Nested values use the same closed algebra, so changing an outer or
inner caller alias cannot change an existing value. Empty, singleton and larger
inputs follow the same rule. The exposed views are unmodifiable even to Java
callers. Object keys still cross the Unicode scalar boundary at construction.

Private typed holders retain data-class equality and hashing without introducing
untyped equality implementations. `ConsistentCopyVisibility` keeps the generated
copy private, matching the private primary constructor; the explicit public
`copy(fields = ...)` / `copy(values = ...)` methods cross the owned boundary again.
Display strings, map/list hash codes, insertion order, precise integer/decimal
category and scale, valid Unicode and canonical digests retain their prior meaning.
This development API is not a binary-compatible published SDK promise.

Draft task/planning record collections are still ordinary typed lists. The shared
reducer re-runs their constructor checks before accepting a prospective universe.
A caller that changes such an alias to invalid contents after construction cannot
obtain a writable bootstrap/ledger plan or commit it through a storage adapter.
The existing import-admission digest check independently protects reviewed source
maps and projections. These guards do not assert that every historical DTO is
deeply immutable or support callers concurrently mutating inputs during an API call.

The six shared regressions cover ownership, nested aliases, exposed collection
views, copies/exact values, bootstrap rejection before target creation, file-plan
and apply rejection, cold reconstruction, and unchanged unrelated bytes/mtimes.
Existing Unicode witnesses now assert ownership instead of expecting a borrowed
map to invalidate an existing value. The old valid canonical witness is unchanged.
Full source, native behavioral and packaged process parity remain required before
closure. No protocol hash version, provider authority, unknown-extension behavior,
implicit Git operation or Native Image reflection allowance changes.
