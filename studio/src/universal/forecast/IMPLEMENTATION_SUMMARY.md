# CUBE Revenue Forecasting - Implementation Summary

## Files Created

### Core Pipeline Scripts

1. **run_forecast.py** (Main Runner)
   - Executes the complete pipeline in sequence
   - Provides final summary with forecasts
   - Usage: `python run_forecast.py`

2. **revenue_forecast.py** (Comprehensive Pipeline)
   - All-in-one script with data loading, profiling, and forecasting
   - Multiple model approaches (time series, regression, ensemble)
   - Usage: `python revenue_forecast.py`

3. **exploratory_analysis.py** (Data Profiling)
   - Loads and profiles both datasets
   - Analyzes trends, seasonality, and patterns
   - Generates visualization charts
   - Usage: `python exploratory_analysis.py`

4. **feature_engineering.py** (Feature Creation)
   - Creates lag features from revenue data
   - Aggregates credit card data by quarter
   - Merges datasets into unified feature set
   - Usage: `python feature_engineering.py`

5. **forecasting_models.py** (Model Training)
   - Trains multiple forecasting models
   - Generates Q4 2025 forecast
   - Calculates confidence intervals
   - Usage: `python forecasting_models.py`

### Supporting Files

6. **test_pipeline.py** (Verification)
   - Quick test to verify all components work
   - Shows sample forecast output
   - Usage: `python test_pipeline.py`

7. **README.md** (Documentation)
   - Complete documentation of the pipeline
   - Usage instructions and customization guide
   - Methodology notes

## Quick Start

```bash
# Run the complete pipeline
python run_forecast.py

# Or run individual components
python test_pipeline.py          # Quick verification
python exploratory_analysis.py   # Data profiling
python feature_engineering.py    # Feature creation
python forecasting_models.py     # Model training & forecasting
```

## Data Structure

### Revenue Data (input/revenue.csv)
- **Columns**: period, actual, pct_change
- **Format**: Quarterly data from 2018Q4 to 2026Q1
- **Example**:
  ```
  period,actual,pct_change
  2025Q3,242.165,0.011271
  ```

### Credit Card Data (input/creditcard.csv)
- **Columns**: 33 columns including:
  - `txn_date`: Transaction date
  - `spend_raw`: Transaction amount
  - `mem_state`: Member state
  - `mem_income_bin`: Income bracket
  - `constind`: Consistent customer indicator
- **Records**: 13 transactions (sample data)

## Key Features Created

### Revenue Features
- Lag features (Q-1, Q-2, Q-3, Q-4)
- Year-over-year changes
- Rolling averages (2, 4, 8 quarters)
- Growth momentum and acceleration
- Quarter dummy variables
- Time index

### Credit Card Features
- Total quarterly spend
- Transaction count and statistics
- Consistent vs new customer spend
- Geographic concentration (HHI)
- Income distribution
- Lagged credit card metrics

## Forecasting Models

### 1. Time Series Model
- Uses linear trend + seasonal indices
- Simple and interpretable
- Best for stable, seasonal patterns

### 2. Ridge Regression
- Uses all available features
- Regularized to prevent overfitting
- Captures complex relationships

### 3. Gradient Boosting
- Ensemble of decision trees
- Captures non-linear patterns
- Good for complex relationships

### 4. Ensemble Forecast
- Weighted average of all models
- More robust than individual models
- Primary forecast output

## Sample Output

```
🔮 Q4 2025 FORECAST:
   - Time Series Model: $237.39M
   - Ridge Regression: $238.50M
   - Gradient Boosting: $236.80M

   📌 ENSEMBLE FORECAST: $237.56M
   📌 95% CONFIDENCE INTERVAL: $232.10M - $243.02M
   📌 YoY Growth (vs 2024Q4): +1.2%
```

## Output Files

| File | Description |
|------|-------------|
| `output/revenue_analysis.png` | Revenue trend visualizations |
| `output/creditcard_analysis.png` | Credit card spend visualizations |
| `output/features_merged.csv` | Combined feature dataset |
| `output/feature_summary.csv` | Feature statistics |

## Requirements

```
pandas>=1.3.0
numpy>=1.20.0
scikit-learn>=1.0.0
matplotlib>=3.4.0
seaborn>=0.11.0
```

Install with:
```bash
pip install pandas numpy scikit-learn matplotlib seaborn
```

## Next Steps

1. **Run the pipeline**: `python run_forecast.py`
2. **Review visualizations**: Check `output/` directory
3. **Analyze features**: Review `output/features_merged.csv`
4. **Customize models**: Edit `forecasting_models.py` to adjust parameters
5. **Add more data**: Place additional data in `input/` directory

## Notes

- The pipeline is designed to work with the provided sample data
- For production use, ensure credit card data covers the full time period
- The ensemble forecast typically provides the most reliable estimate
- Confidence intervals account for model uncertainty but not external shocks

## Support

For questions or issues:
1. Review the inline documentation in each script
2. Check `README.md` for detailed methodology
3. Run `test_pipeline.py` to verify setup