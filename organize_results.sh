#!/bin/bash

# Results Organization Script for CloudSim Hippopotamus Optimization Research
# This script helps organize research results and clean up old experiment directories

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
RESULTS_DIR="results"
BACKUP_DIR="results_backup_$(date +%Y%m%d_%H%M%S)"
MAX_OLD_RESULTS=10  # Keep only the latest 10 old result directories

# Function to print colored output
print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_header() {
    echo -e "${BLUE}================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}================================${NC}"
}

# Function to check if directory exists and is not empty
check_directory() {
    if [ ! -d "$1" ]; then
        print_error "Directory $1 does not exist"
        return 1
    fi
    
    if [ -z "$(ls -A $1 2>/dev/null)" ]; then
        print_warning "Directory $1 is empty"
        return 1
    fi
    
    return 0
}

# Function to organize existing results
organize_existing_results() {
    print_header "Organizing Existing Results"
    
    if ! check_directory "$RESULTS_DIR"; then
        return 1
    fi
    
    # Create backup
    print_status "Creating backup of existing results..."
    cp -r "$RESULTS_DIR" "$BACKUP_DIR"
    print_status "Backup created at: $BACKUP_DIR"
    
    # Create organized structure
    print_status "Creating organized structure..."
    mkdir -p "$RESULTS_DIR/organized_results"
    mkdir -p "$RESULTS_DIR/organized_results/baseline_experiments"
    mkdir -p "$RESULTS_DIR/organized_results/real_dataset_experiments"
    mkdir -p "$RESULTS_DIR/organized_results/scalability_experiments"
    mkdir -p "$RESULTS_DIR/organized_results/sensitivity_experiments"
    mkdir -p "$RESULTS_DIR/organized_results/single_experiments"
    mkdir -p "$RESULTS_DIR/organized_results/other_experiments"
    
    # Move existing results to appropriate categories
    print_status "Categorizing existing results..."
    
    for dir in "$RESULTS_DIR"/*; do
        if [ -d "$dir" ] && [ "$(basename "$dir")" != "organized_results" ] && [ "$(basename "$dir")" != "exported_data" ]; then
            dirname=$(basename "$dir")
            
            case "$dirname" in
                baseline_comparison_*)
                    print_status "Moving $dirname to baseline_experiments"
                    mv "$dir" "$RESULTS_DIR/organized_results/baseline_experiments/"
                    ;;
                real_dataset_*)
                    print_status "Moving $dirname to real_dataset_experiments"
                    mv "$dir" "$RESULTS_DIR/organized_results/real_dataset_experiments/"
                    ;;
                scalability_*)
                    print_status "Moving $dirname to scalability_experiments"
                    mv "$dir" "$RESULTS_DIR/organized_results/scalability_experiments/"
                    ;;
                sensitivity_*)
                    print_status "Moving $dirname to sensitivity_experiments"
                    mv "$dir" "$RESULTS_DIR/organized_results/sensitivity_experiments/"
                    ;;
                single_experiment_*)
                    print_status "Moving $dirname to single_experiments"
                    mv "$dir" "$RESULTS_DIR/organized_results/single_experiments/"
                    ;;
                research_*|full_research_*)
                    print_status "Moving $dirname to other_experiments (research run)"
                    mv "$dir" "$RESULTS_DIR/organized_results/other_experiments/"
                    ;;
                *)
                    print_status "Moving $dirname to other_experiments"
                    mv "$dir" "$RESULTS_DIR/organized_results/other_experiments/"
                    ;;
            esac
        fi
    done
    
    print_status "Results organization completed!"
    print_status "Organized results are in: $RESULTS_DIR/organized_results/"
}

# Function to clean up old results
cleanup_old_results() {
    print_header "Cleaning Up Old Results"
    
    if ! check_directory "$RESULTS_DIR"; then
        return 1
    fi
    
    # Find old result directories (excluding organized_results and exported_data)
    old_dirs=($(find "$RESULTS_DIR" -maxdepth 1 -type d -name "*" ! -name "organized_results" ! -name "exported_data" ! -name "results" | sort -r | tail -n +$((MAX_OLD_RESULTS + 1))))
    
    if [ ${#old_dirs[@]} -eq 0 ]; then
        print_status "No old results to clean up"
        return 0
    fi
    
    print_warning "Found ${#old_dirs[@]} old result directories to remove:"
    for dir in "${old_dirs[@]}"; do
        echo "  - $(basename "$dir")"
    done
    
    read -p "Do you want to proceed with cleanup? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        for dir in "${old_dirs[@]}"; do
            print_status "Removing $(basename "$dir")"
            rm -rf "$dir"
        done
        print_status "Cleanup completed!"
    else
        print_status "Cleanup cancelled"
    fi
}

# Function to show results summary
show_results_summary() {
    print_header "Results Summary"
    
    if ! check_directory "$RESULTS_DIR"; then
        return 1
    fi
    
    echo "Current Results Structure:"
    echo
    
    if [ -d "$RESULTS_DIR/organized_results" ]; then
        echo "Organized Results:"
        for category in baseline_experiments real_dataset_experiments scalability_experiments sensitivity_experiments single_experiments other_experiments; do
            if [ -d "$RESULTS_DIR/organized_results/$category" ]; then
                count=$(find "$RESULTS_DIR/organized_results/$category" -maxdepth 1 -type d | wc -l)
                echo "  - $category: $count directories"
            fi
        done
        echo
    fi
    
    echo "Root Level Results:"
    for dir in "$RESULTS_DIR"/*; do
        if [ -d "$dir" ]; then
            dirname=$(basename "$dir")
            if [ "$dirname" != "organized_results" ] && [ "$dirname" != "exported_data" ]; then
                count=$(find "$dir" -maxdepth 1 -type d 2>/dev/null | wc -l)
                echo "  - $dirname: $count subdirectories"
            fi
        fi
    done
    
    echo
    echo "Total disk usage:"
    du -sh "$RESULTS_DIR" 2>/dev/null || echo "Unable to calculate disk usage"
}

# Function to show help
show_help() {
    print_header "Results Organization Script"
    echo "Usage: $0 [OPTION]"
    echo
    echo "Options:"
    echo "  organize    Organize existing results into categories"
    echo "  cleanup     Clean up old result directories (keeps latest $MAX_OLD_RESULTS)"
    echo "  summary     Show summary of current results structure"
    echo "  help        Show this help message"
    echo
    echo "Examples:"
    echo "  $0 organize    # Organize existing results"
    echo "  $0 cleanup     # Clean up old results"
    echo "  $0 summary     # Show results summary"
    echo
    echo "Note: The organize option creates a backup before reorganizing."
}

# Main script logic
case "${1:-help}" in
    organize)
        organize_existing_results
        ;;
    cleanup)
        cleanup_old_results
        ;;
    summary)
        show_results_summary
        ;;
    help|--help|-h)
        show_help
        ;;
    *)
        print_error "Unknown option: $1"
        echo
        show_help
        exit 1
        ;;
esac 