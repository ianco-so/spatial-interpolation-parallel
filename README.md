# Spatial Interpolation Parallel :earth_africa: :thermometer:

A high-performance Java implementation of **Spatial Interpolation** algorithms (such as IDW - Inverse Distance Weighting) designed to explore **Concurrent Programming** paradigms. This project is part of the coursework for the Concurrent Programming discipline at the Federal University of Rio Grande do Norte (**UFRN**).

## :dart: Objective
The primary goal is to solve the spatial interpolation problem—calculating unknown values for geographical points based on known scattered data—by leveraging multi-core architectures to achieve significant performance gains over sequential implementations.

## 	:hammer_and_wrench: Tech Stack
* **Java 21**: Utilizing modern concurrency APIs.
* **Maven**: Dependency management and build automation.
* **JUnit 5**: Ensuring mathematical correctness through unit testing.
* **JMH (Java Microbenchmark Harness)**: For precise performance and speedup measurement.

## :building_construction: Project Structure
Following the standard Maven convention:
* `src/main/java`: Contains the core mathematical logic and parallel strategies.
* `src/test/java`: Contains validation tests and performance benchmarks.

## :rocket: Getting Started

### Prerequisites
- JDK 25 or higher (to access the latest concurrency features).
- Apache Maven 3.x

### Build
```bash
# Clone the repository
git clone <link>

# Enter the project
cd spatial-interpolation-parallel

# Build (or compile) the project
mvn clean package
# or for a faster development compile:
mvn -q -DskipTests compile
```

### Usage overview
This project currently exposes two application entry points (modes): the sequential implementation (`serial`) and the platform-threads implementation (`platform-threads`).

The Maven `exec` executions available are:
- `serial-idw` → runs the sequential main (`br.edu.ufrn.idw.SerialIDWApp`).
- `platform-idw` → runs the platform-threads main (`br.edu.ufrn.idw.PlatformThreadsIDWApp`).
- `targets-generate` → runs the `TargetsGenerator`.
- `sensors-generate` → runs the `inputSensorsGenerator`.

Below are concrete examples showing how to generate datasets and run each mode.

### Generators (create input files)

1) Generate targets (IDs with lat/lon)

```
# Generate 100 targets into data/targets.csv
mvn -q exec:java@targets-generate -Dexec.args="100 data/targets.csv"
```

Notes:
- Arguments for `TargetsGenerator` (in order): `targetCount` `outputFile`.

2) Generate sensors (large measurement file)

```
# Generate ~1GB of sensor records into data/sensors_1gb.csv
# Arguments (in order): considerTargets totalRecords targetsFile outputFile
# Example: do not consider targets
mvn -q exec:java@sensors-generate -Dexec.args="false 10737418 data/targets.csv data/sensors_1gb.csv"

# Example: consider targets (targets must exist beforehand)
mvn -q exec:java@sensors-generate -Dexec.args="true 10737418 data/targets.csv data/sensors_1gb.csv"
```

Important: if you pass `considerTargets=true`, the generator will try to avoid coordinates that match any target; therefore you must generate targets first. Also be aware there is additional CPU overhead when `considerTargets=true`, especially if the number of targets is large.

### Running the applications

Common argument order for both apps is: `inputFile targetFile power`.

Examples:

```
# Run sequential implementation
mvn -q exec:java@serial-idw -Dexec.args="data/sensors_1gb.csv data/targets.csv 2.0"

# Run platform-threads implementation
mvn -q exec:java@platform-idw -Dexec.args="data/sensors_1gb.csv data/targets.csv 2.0"
```

Replace `2.0` above with the desired IDW power parameter.

### Benchmarking with hyperfine

You can benchmark the two modes using `hyperfine`. Example comparing `serial` vs `platform-threads`:

```bash
hyperfine --warmup 3 \
	'mvn -q exec:java@serial-idw -Dexec.args="data/sensors_1gb.csv data/targets.csv 2.0"' \
	'mvn -q exec:java@platform-idw -Dexec.args="data/sensors_1gb.csv data/targets.csv 2.0"' \
	--runs 5
```

Tips:
- Use `--prepare` with `hyperfine` to warm the filesystem cache if you want to measure pure CPU interpolation (e.g. `--prepare "echo warming"`).
- Start with smaller datasets when iterating locally to reduce wall-clock time.

### Argument references (concise)
- `TargetsGenerator`: `targetCount outputFile`
- `inputSensorsGenerator`: `considerTargets totalRecords targetsFile outputFile` (if `considerTargets=true`, generate targets first)
- `SerialIDWApp` / `PlatformThreadsIDWApp`: `inputFile targetFile power`

---

## :bar_chart: Parallel Strategies Implemented
- [ ] **Sequential**: Baseline for performance comparison.
- [ ] **ExecutorService**: Thread pool-based task distribution.
- [ ] **Fork/Join Framework**: Recursive decomposition for grid processing.
- [ ] **Virtual Threads (Project Loom)**: Exploring lightweight concurrency (if applicable).

## :chart_with_upwards_trend: Results & Analysis
*(This section will be updated with Speedup and Efficiency charts comparing Sequential vs. Parallel execution times.)*

---
Developed by Ianco - Computer Science @ UFRN.
