#!/bin/bash

echo "АНАЛИЗ ПОДСКАЗОК В УРОКАХ 36-42"
echo "================================="
echo ""

for lesson in lesson_36_B20.csv lesson_37_B21.csv lesson_38_B22.csv lesson_39_B23.csv lesson_40_B24.csv lesson_41_B25.csv lesson_42_B26.csv; do
    name=$(basename "$lesson" .csv)
    total=$(tail -n +2 "$lesson" | wc -l)
    
    # Lines with at least one hint in parentheses
    with_hints=$(tail -n +2 "$lesson" | grep -P "^[^;]*\([^)]*\)" | wc -l)
    
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
    echo ""
done
