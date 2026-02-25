---
name: analyze
description: Analyze the data and create a detailed report.
---
Generate a comprehensive report from data in: {{args}}

Please analyze the data and create a detailed report:

1. **Data Discovery:**
    - List all data files in the directory: !{find "{{args}}" -type f \( -name "*.csv" -o -name "*.json" -o -name "*.xlsx" -o -name "*.txt" -o -name "*.log" \) | head -15}
    - Identify file types and formats
    - Check file sizes and dates: !{ls -lah "{{args}}" | head -10}
    - Sample file contents: !{head -5 "{{args}}"/*.csv 2>/dev/null || head -5 "{{args}}"/*.txt 2>/dev/null || echo "No readable data files found"}

2. **Data Structure Analysis:**
    - Identify columns/fields in structured data
    - Determine data types (numerical, categorical, dates)
    - Check for headers and data quality issues
    - Identify missing values or inconsistencies

3. **Statistical Summary:**
    - Calculate basic statistics (count, mean, median, mode)
    - Identify data ranges and distributions
    - Find outliers and anomalies
    - Calculate growth rates or trends if time-series data

4. **Data Insights:**
    - Identify key patterns and trends
    - Find correlations between variables
    - Highlight significant findings
    - Compare different time periods or categories

5. **Report Structure:**
   Create a professional report with:
    - **Executive Summary:** Key findings and recommendations
    - **Data Overview:** Description of data sources and methodology
    - **Key Metrics:** Important numbers and statistics
    - **Trends Analysis:** Historical patterns and projections
    - **Insights & Recommendations:** Actionable conclusions

6. **Visualizations (Text-based):**
    - Create ASCII charts and graphs where helpful
    - Use tables for comparisons
    - Include sparkline-style indicators
    - Suggest chart types for visual presentation

7. **Quality Assessment:**
    - Data completeness percentage
    - Accuracy and reliability notes
    - Limitations and caveats
    - Recommendations for data improvement

8. **Action Items:**
    - Specific recommendations based on findings
    - Priority areas for attention
    - Follow-up analysis suggestions
    - Data collection improvements

Please provide a comprehensive, professional report that transforms raw data into actionable business intelligence.

Example outputs:
- Sales increased 15% QoQ with the strongest growth in Q3
- Customer satisfaction scores show 85% positive rating
- Top performing category: Electronics (40% of revenue)
