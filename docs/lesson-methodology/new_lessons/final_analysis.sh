#!/bin/bash

echo "АНАЛИЗ ПОДСКАЗОК В УРОКАХ 36-42"
echo "================================="
echo ""

for lesson in lesson_36_B20.csv lesson_37_B21.csv lesson_38_B22.csv lesson_39_B23.csv lesson_40_B24.csv lesson_41_B25.csv lesson_42_B26.csv; do
    name=$(basename "$lesson" .csv)
    
    # Create temp file for analysis
    tmpfile=$(mktemp)
    tail -n +2 "$lesson" > "$tmpfile"
    
    # Get total lines
    total=$(wc -l < "$tmpfile")
    
    # Count lines with hints in Russian text (before semicolon)
    with_hints=0
    while IFS= read -r line; do
        russian=$(echo "$line" | cut -d';' -f1)
        if echo "$russian" | grep -qP "\([^)]*\)"; then
            ((with_hints++))
        fi
    done < "$tmpfile"
    
    # Lines without hints
    without_hints=$((total - with_hints))
    
    # Calculate percentage
    if [ $total -gt 0 ]; then
        percent=$(awk "BEGIN {printf \"%.1f\", ($with_hints/$total)*100}")
    else
        percent="0.0"
    fi
    
    echo "Урок $name:"
    echo "  Всего строк: $total"
    echo "  С подсказками: $with_hints ($percent%)"
    echo "  Без подсказок: $without_hints"
    
    # Show first 3 lines without hints (if any)
    if [ $without_hints -gt 0 ]; then
        echo "  Примеры строк без подсказок:"
        while IFS= read -r line; do
            russian=$(echo "$line" | cut -d';' -f1)
            if ! echo "$russian" | grep -qP "\([^)]*\)"; then
                echo "    - $russian"
                count=$((count + 1))
                if [ $count -ge 3 ]; then
                    break
                fi
            fi
        done < "$tmpfile"
    fi
    echo ""
    
    rm "$tmpfile"
done
