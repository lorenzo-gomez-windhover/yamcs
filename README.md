# Yamcs Mission Control ![Maven Central](https://img.shields.io/maven-central/v/org.yamcs/yamcs.svg?label=release)

* Website: https://yamcs.org
* Mailing list: [Google Groups](https://groups.google.com/group/yamcs/)

Yamcs is a mission control framework developed in Java. It uses an open-ended architecture that allows tailoring its feature set using yaml configuration files. You can also extend the default feature set by writing custom Java classes.

To start developing your own Yamcs application, follow our [Getting Started](https://yamcs.org/getting-started) guide.


## Documentation

* Server Manual: https://docs.yamcs.org/yamcs-server-manual/
* Javadoc: https://yamcs.org/javadoc/yamcs/index.html?overview-summary.html


## License

Yamcs is licensed under Affero GPLv3.

For commercial licensing please contact [Space Applications Services](https://www.spaceapplications.com) with your use case.


## Development Setup

To work on the core components of Yamcs you need JDK8, Maven and npm.

Build Java jars:

    mvn clean install -DskipTests

Build web interface:

    cd yamcs-web/src/main/webapp
    npm install
    npm run build
    cd -

These commands will produce an optimized production version of the web interface. This process will take a few minutes. For faster incremental builds run in watch mode (`npm run watch`).

For demo and development purposes we work with an all-in-one simulation environment that uses many Yamcs features. In this simulation, Yamcs receives TM from a simple simulator of a landing spacecraft. Yamcs can also send some basic TC. The simulator starts together with Yamcs as a subprocess.

    ./run-example.sh simulation

This configuration stores data to `/storage/yamcs-data`. Ensure this folder exists and that you can write to it.

When Yamcs started successfully, you can visit the built-in web interface by navigating to `http://localhost:8090`.

## Packaging(quick and dirty)

1. In the `pom.xml` file, change the following lines:
from
```
<additionalOption>-Xdoclint:html</additionalOption>
<additionalOption>-Xdoclint:reference</additionalOption>
```
to 
```
<!--additionalOption>-Xdoclint:html</additionalOption-->
<!--additionalOption>-Xdoclint:reference</additionalOption-->
```
.
This disables potential errors with javadoc when packaging yamcs.

**NOTE**: This is simply a _workaround_ for now. It is HIGHLY recomended to get the official release from [here](https://github.com/yamcs/yamcs).
This is just a quick-and-dirty way of packaging yamcs.

2. `mvn package -Drelease -DskipTests`

3. `mkdir opt/yamcs`

4. `tar -xzf distribution/target/yamcs-5.3.6-SNAPSHOT-linux-x86_64.tar.gz --strip-components=1 -C "/opt/yamcs"`

Your tar file might look slightly different depending on YAMCS version you are using.

That's it. YAMCS is installed inside your `/opt/yamcs`.

## Contributions

While Yamcs is managed and developed by Space Applications Services, we also consider pull requests from other contributors. For non-trivial patches we ask you to sign our [CLA](https://yamcs.org/static/Yamcs_Contributor_Agreement_v2.0.pdf).
