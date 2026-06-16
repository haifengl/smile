# CUBE Revenue Forecasting Pipeline

Automated pipeline for estimating CUBE's quarterly topline revenue using historical data and credit card transaction indicators.

## Quick Start

```bash
# Run the complete pipeline
python run_forecast.py

# Or run individual steps
python exploratory_analysis.py    # Data profiling
python feature_engineering.py     # Feature creation
python forecasting_models.py      # Model training & forecasting
```

## Pipeline Overview

```
┌─────────────────────────────────────────────────────────────┐
│  1. exploratory_analysis.py                                 │
│     - Load and profile revenue & credit card data           │
│     - Identify data quality issues                          │
│     - Analyze trends and seasonality                        │
│     - Generate visualization charts                         │
├─────────────────────────────────────────────────────────────┤
│  2. feature_engineering.py                                  │
│     - Create lag features (Q-1, Q-2, Q-4)                   │
│     - Aggregate credit card data by quarter                 │
│     - Compute growth rates, ratios, seasonal indices        │
│     - Merge datasets into unified feature set               │
├─────────────────────────────────────────────────────────────┤
│  3. forecasting_models.py                                   │
│     - Train time series model (trend + seasonality)         │
│     - Train Ridge regression (all features)                 │
│     - Train Gradient Boosting (ensemble)                    │
│     - Generate Q4 2025 forecast                             │
│     - Create ensemble prediction                            │
│     - Calculate confidence intervals                        │
└─────────────────────────────────────────────────────────────┘
```

## Input Data

### Revenue Data (`input/revenue.csv`)
- **Format**: CSV with columns: `quarter`, `revenue`, `growth_rate`
- **Example**:
  ```
  quarter,revenue,growth_rate
  2025Q3,242.165,0.011271
  ```

### Credit Card Data (`input/creditcard.csv`)
- **Format**: Tab-separated CSV
- **Expected columns**:
  - Transaction date
  - Transaction amount/spend
  - State
  - Income bin
  - Consistent customer flag

## Output Files

| File | Description |
|------|-------------|
| `output/revenue_analysis.png` | Revenue trend visualizations |
| `output/creditcard_analysis.png` | Credit card spend visualizations |
| `output/features_merged.csv` | Combined feature dataset |
| `output/feature_summary.csv` | Feature statistics |

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

## Key Features

### Revenue Features
- Lag features (Q-1, Q-2, Q-3, Q-4)
- Year-over-year changes
- Rolling averages (2, 4, 8 quarters)
- Growth momentum and acceleration
- Quarter dummy variables

### Credit Card Features
- Total quarterly spend
- Transaction count and statistics
- Consistent vs new customer spend
- Geographic concentration (HHI)
- Income distribution
- Lagged credit card metrics

## Interpreting Results

### Forecast Output
```
🔮 Q4 2025 FORECAST:
   - Time Series Model: $243.50M (R²: 0.9521)
   - Ridge Regression: $244.20M (R²: 0.9487)
   - Gradient Boosting: $242.80M (R²: 0.9612)

   📌 ENSEMBLE FORECAST: $243.50M
   📌 95% CONFIDENCE INTERVAL: $238.20M - $248.80M
   📌 YoY Growth (vs 2024Q4): +3.7%
```

### Confidence Interval
- Based on prediction variance across models
- 95% confidence level
- Accounts for model uncertainty

## Customization

### Adjust Forecast Quarter
Edit `run_forecast.py` and change the target quarter:
```python
forecast_features = create_forecast_features(df, '2025Q4')  # Change to desired quarter
```

### Add New Features
Edit `feature_engineering.py` to add custom features:
```python
def create_custom_features(df):
    # Add your feature engineering logic here
    df['custom_feature'] = ...
    return df
```

### Modify Models
Edit `forecasting_models.py` to adjust model parameters:
```python
model = GradientBoostingRegressor(
    n_estimators=100,  # Increase for more accuracy
    max_depth=3,       # Adjust for complexity
    learning_rate=0.1  # Tune learning rate
)
```

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

## Troubleshooting

### "No processed data found"
Run `feature_engineering.py` first to create the merged feature dataset.

### "Insufficient features"
Check that credit card data has the required columns (date, amount, etc.).

### "Missing values in forecast"
The pipeline automatically fills missing values, but results may be less reliable.

## Methodology Notes

### Seasonality Handling
- Q4 seasonal index calculated as: `avg(Q4 revenue) / avg(all revenue)`
- Applied multiplicatively to trend forecast

### Leading Indicators
- Credit card spend in Q3 may predict Q4 revenue
- Lag correlation analysis identifies optimal lag period

### Model Selection
- Time series: Good for baseline, captures seasonality
- Regression: Good when credit card data is predictive
- Ensemble: Best for robust, generalizable forecasts

## Contact

For questions or issues, review the inline documentation in each script.