# Historical timestamp compatibility goldens

These are synthetic old-format assertions, not evidence of completed work.
They substitute deliberately non-ISO text into public taskctl examples to test
the historical `recorded_at` contract. The imported revision also uses an
explicitly synthetic manifest identity; this is a codec specimen, not an admitted
import history.

The unchanged codecs at `4233a65b42c482e8fc78ef18ab2eb483d376181e`
passed `HistoricalTimeGoldenTest` before timestamp implementation changes.
`expected.json` pins exact JSON-with-newline SHA-256 values and both versioned
task-revision identities. Those revision identities were independently calculated
from the documented framed canonical encoding and checked against the old codec.
Keep these values fixed when evolving new envelopes.
