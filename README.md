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
* JDK 21
* Apache Maven 3.x

### Installation & Build
```bash
# Clone the repository
git clone [https://github.com/YOUR_USERNAME/spatial-interpolation-parallel.git](https://github.com/YOUR_USERNAME/spatial-interpolation-parallel.git)

# Navigate to the folder
cd spatial-interpolation-parallel

# Build the project
mvn clean package
```
## :bar_chart: Parallel Strategies Implemented
- [ ] **Sequential**: Baseline for performance comparison.
- [ ] **ExecutorService**: Thread pool-based task distribution.
- [ ] **Fork/Join Framework**: Recursive decomposition for grid processing.
- [ ] **Virtual Threads (Project Loom)**: Exploring lightweight concurrency (if applicable).

## :chart_with_upwards_trend: Results & Analysis
*(This section will be updated with Speedup and Efficiency charts comparing Sequential vs. Parallel execution times.)*

---
Developed by Ianco - Computer Science @ UFRN.
