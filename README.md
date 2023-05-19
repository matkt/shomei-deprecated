# Shomei zkevm state manager


**Shomei zkevm state manager** extends the Hyperledger Besu functionality.
This component communicates with Besu in order to maintain and update the zkevm state

## Shomei developers

* [Contribution Guidelines](CONTRIBUTING.md)
* [Coding Conventions](https://wiki.hyperledger.org/display/BESU/Coding+Conventions)


## Binary Releases

Binary releases are available from the [releases page](https://github.com/ConsenSys/shomei/releases).
Binary builds that track the latest changes on the main branch are available on
[Dockerhub](https://hub.docker.com/r/consensys/linea-shomei) 

We recommend only using release versions for Mainnet, but `develop` builds are useful for testing
the latest changes on testnets.

## Build Instructions

### Install Prerequisites

* Java 17

### Build and Test

To build, clone this repo and run with `gradle`:

```shell script
git clone https://github.com/ConsenSys/shomei.git
cd shomei && ./gradlew
```

After a successful build, distribution packages are available in `build/distributions`.

### Other Useful Gradle Targets

| Target      | Builds                                                  |
|-------------|---------------------------------------------------------|
| distTar     | Full distribution in build/distributions (as `.tar.gz`) |
| distZip     | Full distribution in build/distributions (as `.zip`)    |
| installDist | Expanded distribution in `build/install/shomei`         |
| distDocker  | The `consensys/linea-shomei` docker image                     |

## Code Style

We use Google's Java coding conventions for the project. To reformat code, run: 

```shell script 
./gradlew spotlessApply
```

Code style is checked automatically during a build.

## Testing

All the unit tests are run as part of the build, but can be explicitly triggered with:

```shell script 
./gradlew test
```

