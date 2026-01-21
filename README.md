<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="logo/dark.svg">
  <source media="(prefers-color-scheme: light)" srcset="logo/dark.svg">
  <img alt="CloudSim-HO-Research-V2" src="logo/dark.svg" width="500">
</picture>

<br/>
<br/>

**A research framework for evaluating the Hippopotamus Optimization (HO) algorithm for Virtual Machine placement in cloud data centers.**

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen?style=for-the-badge)](.)
[![License](https://img.shields.io/badge/license-MIT-blue?style=for-the-badge)](./LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)](https://maven.apache.org/)

[Documentation](https://cloudsim-ho-project.puneetchandna.com/) · [Contributing](CONTRIBUTING.md) · [Report Bug](https://github.com/puneet-chandna/cloudsim-ho-research-V2/issues)

</div>

---

## ✨ Highlights

- 🦛 **Hippopotamus Optimization (HO)** — A comprehensive implementation of the HO algorithm for VM placement.
- 📊 **Comparative Analysis** — Robust benchmarking against FirstFit, BestFit, and Genetic Algorithm (GA) strategies.
- 🔬 **Parameter Sensitivity Analysis** — In-depth studies on algorithm parameters and scalability.
- 📈 **Detailed Metrics** — Resource utilization, SLA violations, and power consumption analysis.

---

## 🚀 Quick Start

### Prerequisites

Ensure you have the following installed:

| Tool  | Version |
| :---- | :------ |
| Java  | 21+     |
| Maven | 3.9+    |

### Installation

```bash
# Clone the repository
git clone https://github.com/puneet-chandna/cloudsim-ho-research-V2.git

# Navigate to the project directory
cd cloudsim-ho-research-V2

# Build the project
mvn clean install
```

---

## ⚙️ Usage

### Running Experiments

Run the default experiment suite (Micro, Small, and Medium scenarios):

<details>
<summary><strong>PowerShell</strong></summary>

```powershell
./run-experiment.ps1
```

</details>

<details>
<summary><strong>Bash</strong></summary>

```bash
./run-experiment.sh
```

</details>

### Running the Simulation from JAR

Execute the simulation directly from the compiled JAR file:

<details>
<summary><strong>PowerShell</strong></summary>

```powershell
./run-simulation.ps1
```

</details>

<details>
<summary><strong>Bash</strong></summary>

```bash
./run-simulation.sh
```

</details>

---

## 📚 Documentation

For comprehensive guides, API references, and conceptual explanations, visit the **[official documentation](https://cloudsim-ho-project.puneetchandna.com/)**.

---

## 🤝 Contributing

Contributions are welcome! Please see our [Contributing Guide](CONTRIBUTING.md) for details on how to get started.

---

## 📜 Code of Conduct

This project adheres to a [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code.

---

## 📄 License

This project is licensed under the [MIT License](./LICENSE).

---

## 🙏 Acknowledgments

This project is built on top of **[CloudSim Plus](https://cloudsimplus.org/)**, a modern and full-featured framework for modeling and simulating cloud computing environments.

---

<div align="center">

Made with ❤️ by [Puneet Chandna](https://github.com/puneet-chandna)

</div>
