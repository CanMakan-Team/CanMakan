# ETL tools

Offline helpers under this package (for example `OpenFoodFactsSqlGenerator`) are **not** part of the normal API runtime.

They are activated only with the Spring profile `generate-sql` and are used to turn local Open Food Facts dumps into SQL seed scripts. Leave them out of everyday backend runs.
