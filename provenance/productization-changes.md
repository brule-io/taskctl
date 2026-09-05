# Changes after the frozen extraction proof

The extraction reports and original source manifests retain their original
identities. They describe the proof baseline, not every later product revision.

Productization adds a native filesystem ledger, greenfield initializer, nominal
identity and revision algebra, standalone release/bootstrap machinery, and a
reference generator. Native v1 remains unfrozen; legacy consumers are unchanged.

The historical compatibility adapter's TaskRef/RoadmapRef/EpicRef/AdrRef parsing
grammars are unchanged. RoadmapRef, EpicRef and AdrRef construction is now private,
matching TaskRef. All four use the same parsing convention and maintain constructor
invariants. This closes an in-process API validity bypass without changing admitted
legacy record text, identity mappings, graph behavior or evidence interpretation.
The original transition tests and additional reference tests cover this boundary.
