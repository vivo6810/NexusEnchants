# Third-party notices

NexusEnchants is licensed under the MIT licence (see `LICENSE`). It bundles or depends on the
following third-party components. Versions are pinned in `build.gradle`.

## Bundled in the plugin jar (shaded)

| Component | Version | Licence | Project |
|---|---|---|---|
| HikariCP (`com.zaxxer:HikariCP`) | 7.1.0 | Apache License 2.0 | https://github.com/brettwooldridge/HikariCP |
| SQLite JDBC (`org.xerial:sqlite-jdbc`) | 3.54.1.0 | Apache License 2.0 | https://github.com/xerial/sqlite-jdbc |
| MySQL Connector/J (`com.mysql:mysql-connector-j`) | 9.7.0 | GNU General Public License v2.0 with FOSS exception | https://dev.mysql.com/downloads/connector/j/ |

HikariCP is relocated to `com.nexus.enchants.libs.hikari`. The JDBC drivers are **not** relocated,
because their service descriptors and native resources must remain discoverable.

### Note on MySQL Connector/J licensing

Connector/J is distributed under the GPL v2 with the Universal FOSS Exception, which permits
distribution inside an open-source project licensed under an OSI-approved licence such as MIT. If you
redistribute a modified build of this plugin under a non-FOSS licence, review the Connector/J terms
yourself or depend on the driver at runtime (for example by installing it on the server) instead of
shading it. The plugin does not require MySQL to operate: SQLite is the default and needs only
sqlite-jdbc.

The SQLite JDBC driver embeds SQLite, which is in the **public domain**:
https://www.sqlite.org/copyright.html

## Compile-time only (not bundled)

| Component | Version | Licence | Project |
|---|---|---|---|
| Paper API (`io.papermc.paper:paper-api`) | 26.3.build.200-stable | MIT (PaperMC) / GPL v3 (Bukkit heritage) | https://github.com/PaperMC/Paper |

`compileOnly`: the server provides these classes; they are never packaged.

## Test-only (not bundled)

| Component | Version | Licence | Project |
|---|---|---|---|
| JUnit Jupiter (`org.junit.jupiter:junit-jupiter`) | 6.1.3 | Eclipse Public License 2.0 | https://junit.org |
| MockBukkit (`org.mockbukkit.mockbukkit:mockbukkit-v26.3`) | 4.119.0 | MIT | https://github.com/MockBukkit/MockBukkit |
| SLF4J API + Simple (`org.slf4j`) | 2.0.20 | MIT | https://www.slf4j.org |

## Build tooling

| Tool | Version | Licence |
|---|---|---|
| Gradle (wrapper) | 9.8.2 | Apache License 2.0 |
| Shadow (`com.gradleup.shadow`) | 9.6.1 | Apache License 2.0 |
| Checkstyle | 14.3.0 | LGPL 2.1 |

## Trademarks

"Minecraft" is a trademark of Mojang Synergies AB. "Paper" refers to the PaperMC project. This
project is not affiliated with or endorsed by Mojang or PaperMC.
