"""
CUBE Revenue Forecasting Pipeline
=================================
Estimates CUBE's 2025Q4 topline revenue using:
1. Historical quarterly revenue data
2. Credit card transaction data as leading indicators

Author: Data Analysis Pipeline
Date: 2026-06-16
"""

import pandas as pd
import numpy as np
from datetime import datetime
import warnings
warnings.filterwarnings('ignore')

# ============================================================================
# CONFIGURATION
# ============================================================================

INPUT_DIR = './input'
OUTPUT_DIR = './output'

# ============================================================================
# DATA LOADING
# ============================================================================

def load_revenue_data(filepath):
    """
    Load historical quarterly revenue data.
    Expected columns: period, actual, pct_change
    """
    print("=" * 80)
    print("LOADING REVENUE DATA")
    print("=" * 80)

    # Read the CSV file
    df = pd.read_csv(filepath)

    # Display basic info
    print(f"\nShape: {df.shape}")
    print(f"\nColumns: {df.columns.tolist()}")
    print(f"\nFirst 5 rows:")
    print(df.head())

    # Parse quarter into year and quarter components
    df['year'] = df['period'].str[:4].astype(int)
    df['qtr'] = df['period'].str[-1].astype(int)

    # Create datetime for the quarter end (approximate)
    quarter_months = {1: '03', 2: '06', 3: '09', 4: '12'}
    df['quarter_end'] = df.apply(
        lambda x: f"{x['year']}-{quarter_months[x['qtr']]}-30", axis=1
    )
    df['quarter_end'] = pd.to_datetime(df['quarter_end'])

    print(f"\nDate range: {df['quarter_end'].min()} to {df['quarter_end'].max()}")
    print(f"Total quarters: {len(df)}")

    return df


def load_creditcard_data(filepath):
    """
    Load credit card transaction data.
    This data contains CUBE transactions with spend, state, zipcode, income, etc.
    """
    print("\n" + "=" * 80)
    print("LOADING CREDIT CARD DATA")
    print("=" * 80)

    # Try to detect delimiter
    with open(filepath, 'r') as f:
        first_line = f.readline()

    # Check if tab-separated
    if '\t' in first_line:
        df = pd.read_csv(filepath, sep='\t')
    else:
        df = pd.read_csv(filepath)

    print(f"\nShape: {df.shape}")
    print(f"\nColumns ({len(df.columns)}):")
    for i, col in enumerate(df.columns):
        print(f"  {i+1}. {col}")

    # Display first few rows
    print(f"\nFirst 3 rows:")
    print(df.head(3))

    return df


# ============================================================================
# DATA PROFILING & QUALITY CHECKS
# ============================================================================

def profile_revenue_data(df):
    """Profile the revenue dataset."""
    print("\n" + "=" * 80)
    print("REVENUE DATA PROFILE")
    print("=" * 80)

    print("\n--- Basic Statistics ---")
    print(df[['actual', 'pct_change']].describe())

    print("\n--- Quarterly Revenue Summary ---")
    quarterly_summary = df.groupby('qtr')['actual'].agg(['mean', 'std', 'min', 'max'])
    print(quarterly_summary)

    print("\n--- Year-over-Year Growth by Quarter ---")
    df_sorted = df.sort_values('period')
    df_sorted['yoy_growth'] = df_sorted['actual'].pct_change(periods=4)
    print(df_sorted[['period', 'actual', 'yoy_growth']].tail(8))

    return df_sorted


def profile_creditcard_data(df):
    """Profile the credit card dataset."""
    print("\n" + "=" * 80)
    print("CREDIT CARD DATA PROFILE")
    print("=" * 80)

    # Identify key columns (based on the data structure we observed)
    # The actual column names need to be inferred from the data

    print("\n--- Column Types ---")
    print(df.dtypes)

    print("\n--- Missing Values ---")
    missing = df.isnull().sum()
    missing_pct = (missing / len(df)) * 100
    missing_df = pd.DataFrame({
        'missing_count': missing,
        'missing_pct': missing_pct
    })
    print(missing_df[missing_df['missing_count'] > 0])

    # Try to identify date column
    date_cols = [col for col in df.columns if 'date' in col.lower() or 'trans' in col.lower()]
    print(f"\n--- Potential Date Columns: {date_cols}")

    # Try to identify amount/spend column
    amount_cols = [col for col in df.columns if 'amount' in col.lower() or 'spend' in col.lower() or 'debit' in col.lower() or 'credit' in col.lower()]
    print(f"--- Potential Amount Columns: {amount_cols}")

    return df


# ============================================================================
# FEATURE ENGINEERING
# ============================================================================

def extract_creditcard_features(df_cc, df_revenue):
    """
    Extract features from credit card data aggregated by quarter.
    These features will be used as leading indicators for revenue forecasting.
    """
    print("\n" + "=" * 80)
    print("FEATURE ENGINEERING: Credit Card Aggregations")
    print("=" * 80)

    # Identify the date column - looking at the data, it seems to be around column index 4
    # Let's find it dynamically
    date_col = None
    amount_col = None
    state_col = None
    income_col = None
    consistent_col = None

    for col in df_cc.columns:
        col_lower = str(col).lower()
        if date_col is None and ('date' in col_lower or '2019' in str(df_cc[col].iloc[0]) if len(df_cc) > 0 else False):
            try:
                pd.to_datetime(df_cc[col].iloc[0])
                date_col = col
            except:
                pass
        if amount_col is None and ('amount' in col_lower or 'spend' in col_lower):
            amount_col = col
        if state_col is None and col_lower == 'state':
            state_col = col
        if income_col is None and 'income' in col_lower:
            income_col = col
        if consistent_col is None and ('consistent' in col_lower or 'repeat' in col_lower):
            consistent_col = col

    print(f"\nIdentified columns:")
    print(f"  Date: {date_col}")
    print(f"  Amount: {amount_col}")
    print(f"  State: {state_col}")
    print(f"  Income: {income_col}")
    print(f"  Consistent Customer: {consistent_col}")

    if date_col is None:
        # Try to find by position - column 4 seems to be the date
        print("\n  Trying to detect date column by position...")
        for i, col in enumerate(df_cc.columns):
            try:
                sample = df_cc[col].iloc[0]
                if isinstance(sample, str) and ('-' in sample or '/' in sample):
                    pd.to_datetime(sample)
                    date_col = col
                    print(f"  Found date column at position {i}: {col}")
                    break
            except:
                continue

    # Convert date column
    if date_col:
        df_cc['transaction_date'] = pd.to_datetime(df_cc[date_col], errors='coerce')
        df_cc['year'] = df_cc['transaction_date'].dt.year
        df_cc['quarter'] = df_cc['transaction_date'].dt.quarter
        df_cc['year_quarter'] = df_cc['transaction_date'].dt.to_period('Q')

        print(f"\nDate range: {df_cc['transaction_date'].min()} to {df_cc['transaction_date'].max()}")

        # Aggregate by quarter
        agg_dict = {
            amount_col: ['sum', 'mean', 'count', 'std'] if amount_col else {}
        }

        # Basic aggregations
        quarterly_agg = df_cc.groupby('year_quarter').agg({
            amount_col: ['sum', 'mean', 'count', 'std'] if amount_col else 'count'
        }).reset_index()

        quarterly_agg.columns = ['year_quarter', 'total_spend', 'avg_spend', 'transaction_count', 'spend_std']

        # Add consistent customer analysis if column exists
        if consistent_col:
            consistent_spend = df_cc.groupby('year_quarter').apply(
                lambda x: x[x[consistent_col] == True][amount_col].sum() if amount_col else 0
            ).reset_index()
            consistent_spend.columns = ['year_quarter', 'consistent_spend']

            total_spend = df_cc.groupby('year_quarter')[amount_col].sum().reset_index()
            total_spend.columns = ['year_quarter', 'total_spend_all']

            quarterly_agg = quarterly_agg.merge(consistent_spend, on='year_quarter', how='left')
            quarterly_agg = quarterly_agg.merge(total_spend, on='year_quarter', how='left')
            quarterly_agg['consistent_ratio'] = quarterly_agg['consistent_spend'] / quarterly_agg['total_spend_all']

        print("\n--- Quarterly Credit Card Aggregations ---")
        print(quarterly_agg)

        return quarterly_agg, df_cc

    return None, df_cc


def create_revenue_features(df):
    """
    Create features from historical revenue data for modeling.
    """
    print("\n" + "=" * 80)
    print("FEATURE ENGINEERING: Revenue Features")
    print("=" * 80)

    df = df.sort_values('quarter').copy()

    # Lag features
    for lag in [1, 2, 3, 4]:
        df[f'revenue_lag_{lag}'] = df['revenue'].shift(lag)
        df[f'growth_lag_{lag}'] = df['growth_rate'].shift(lag)

    # Rolling statistics
    df['revenue_ma4'] = df['revenue'].rolling(window=4).mean()
    df['revenue_ma8'] = df['revenue'].rolling(window=8).mean()
    df['growth_ma4'] = df['growth_rate'].rolling(window=4).mean()

    # YoY growth
    df['yoy_growth'] = df['revenue'].pct_change(periods=4)

    # Quarter dummy variables
    df = pd.get_dummies(df, columns=['qtr'], prefix='q', drop_first=True)

    print("\n--- Revenue Features Created ---")
    print(f"Features: {df.columns.tolist()}")

    return df


# ============================================================================
# SEASONALITY ANALYSIS
# ============================================================================

def analyze_seasonality(df):
    """
    Analyze seasonal patterns in revenue.
    """
    print("\n" + "=" * 80)
    print("SEASONALITY ANALYSIS")
    print("=" * 80)

    # Calculate seasonal indices
    overall_mean = df['actual'].mean()
    seasonal_indices = df.groupby('qtr')['actual'].mean() / overall_mean

    print("\n--- Seasonal Indices by Quarter ---")
    for qtr, idx in seasonal_indices.items():
        print(f"  Q{qtr}: {idx:.4f} ({'+' if idx > 1 else ''}{(idx-1)*100:.1f}% vs average)")

    # Q4 specific analysis
    q4_avg = df[df['qtr'] == 4]['actual'].mean()
    q1_avg = df[df['qtr'] == 1]['actual'].mean()
    q4_vs_q1 = (q4_avg - q1_avg) / q1_avg * 100

    print(f"\n--- Q4 vs Q1 Comparison ---")
    print(f"  Q4 Average Revenue: ${q4_avg:.2f}M")
    print(f"  Q1 Average Revenue: ${q1_avg:.2f}M")
    print(f"  Q4 vs Q1 Difference: {q4_vs_q1:+.1f}%")

    return seasonal_indices


# ============================================================================
# CORRELATION ANALYSIS
# ============================================================================

def analyze_correlations(df_revenue, df_cc_agg):
    """
    Analyze correlations between credit card metrics and revenue.
    """
    print("\n" + "=" * 80)
    print("CORRELATION ANALYSIS")
    print("=" * 80)

    # Merge datasets
    df_revenue['year_quarter'] = df_revenue['quarter_end'].dt.to_period('Q')

    merged = df_revenue.merge(df_cc_agg, on='year_quarter', how='left')

    if 'total_spend' in merged.columns:
        print("\n--- Credit Card Spend vs Revenue Correlation ---")
        corr = merged[['revenue', 'total_spend', 'transaction_count', 'avg_spend']].corr()
        print(corr)

        # Cross-correlation with lag
        print("\n--- Lagged Correlation: CC Spend -> Revenue ---")
        for lag in [1, 2, 3]:
            merged[f'cc_spend_lag_{lag}'] = merged['total_spend'].shift(lag)

        lag_corrs = []
        for lag in [1, 2, 3]:
            valid_data = merged.dropna(subset=[f'cc_spend_lag_{lag}', 'revenue'])
            if len(valid_data) > 4:
                corr = valid_data['revenue'].corr(valid_data[f'cc_spend_lag_{lag}'])
                lag_corrs.append((lag, corr))
                print(f"  CC Spend Lag {lag}Q: r = {corr:.4f}")

        return lag_corrs

    return []


# ============================================================================
# FORECASTING MODELS
# ============================================================================

def forecast_time_series(df):
    """
    Simple time series forecasting using trend + seasonality.
    """
    print("\n" + "=" * 80)
    print("TIME SERIES FORECASTING")
    print("=" * 80)

    # Get the last few quarters for trend calculation
    recent_data = df.tail(8).copy()

    # Calculate trend (linear regression slope)
    x = np.arange(len(recent_data))
    y = recent_data['actual'].values
    slope, intercept = np.polyfit(x, y, 1)

    print(f"\n--- Trend Analysis (Last 8 Quarters) ---")
    print(f"  Slope: ${slope:.3f}M per quarter")
    print(f"  Intercept: ${intercept:.3f}M")

    # Get seasonal indices
    seasonal_indices = df.groupby('qtr')['actual'].mean() / df['actual'].mean()

    # Forecast Q4 2025
    # Find the position for 2025Q4
    last_quarter = df.iloc[-1]
    forecast_quarter = '2025Q4'

    # Calculate base forecast using trend
    n_quarters_ahead = 1  # From 2025Q3 to 2025Q4
    base_forecast = intercept + slope * (len(recent_data) + n_quarters_ahead - 1)

    # Apply seasonal adjustment
    q4_seasonal = seasonal_indices.get(4, 1.0)
    seasonal_forecast = base_forecast * q4_seasonal

    print(f"\n--- Q4 2025 Forecast ---")
    print(f"  Base forecast (trend): ${base_forecast:.2f}M")
    print(f"  Q4 Seasonal index: {q4_seasonal:.4f}")
    print(f"  Seasonal forecast: ${seasonal_forecast:.2f}M")

    return {
        'base_forecast': base_forecast,
        'seasonal_forecast': seasonal_forecast,
        'q4_seasonal_index': q4_seasonal,
        'trend_slope': slope
    }


def forecast_regression(df_revenue, df_cc_agg):
    """
    Regression-based forecasting using credit card data as features.
    """
    print("\n" + "=" * 80)
    print("REGRESSION-BASED FORECASTING")
    print("=" * 80)

    # Prepare features
    df = df_revenue.copy()
    df['year_quarter'] = df['quarter_end'].dt.to_period('Q')

    # Merge with credit card data
    merged = df.merge(df_cc_agg, on='year_quarter', how='left')

    # Create lag features for credit card data
    for lag in [1, 2, 3, 4]:
        merged[f'cc_spend_lag_{lag}'] = merged['total_spend'].shift(lag)
        merged[f'txn_count_lag_{lag}'] = merged['transaction_count'].shift(lag)

    # Drop rows with missing data
    feature_cols = [col for col in merged.columns if 'lag' in col or col.startswith('q_')]
    model_data = merged.dropna(subset=feature_cols + ['actual'])

    if len(model_data) < 8:
        print("  Insufficient data for regression model")
        return None

    # Simple linear regression using recent trend
    X = np.arange(len(model_data)).reshape(-1, 1)
    y = model_data['actual'].values

    # Fit model
    from sklearn.linear_model import LinearRegression
    model = LinearRegression()
    model.fit(X, y)

    # Predict next quarter
    next_x = np.array([[len(model_data)]])
    base_pred = model.predict(next_x)[0]

    # Apply seasonal adjustment
    seasonal_indices = df.groupby('qtr')['actual'].mean() / df['actual'].mean()
    q4_seasonal = seasonal_indices.get(4, 1.0)
    seasonal_pred = base_pred * q4_seasonal

    print(f"\n--- Regression Forecast ---")
    print(f"  Model R²: {model.score(X, y):.4f}")
    print(f"  Base prediction: ${base_pred:.2f}M")
    print(f"  Seasonal adjustment: {q4_seasonal:.4f}")
    print(f"  Final prediction: ${seasonal_pred:.2f}M")

    return {
        'base_prediction': base_pred,
        'seasonal_prediction': seasonal_pred,
        'r_squared': model.score(X, y)
    }


def ensemble_forecast(forecasts):
    """
    Combine multiple forecasts using weighted average.
    """
    print("\n" + "=" * 80)
    print("ENSEMBLE FORECAST")
    print("=" * 80)

    predictions = []

    # Time series forecast
    if 'time_series' in forecasts:
        predictions.append(('Time Series', forecasts['time_series']['seasonal_forecast'], 0.4))

    # Regression forecast
    if 'regression' in forecasts:
        predictions.append(('Regression', forecasts['regression']['seasonal_prediction'], 0.3))

    # Naive seasonal (same quarter last year)
    if 'naive' in forecasts:
        predictions.append(('Naive Seasonal', forecasts['naive'], 0.3))

    if not predictions:
        print("  No forecasts available for ensemble")
        return None

    # Calculate weighted average
    total_weight = sum(w for _, _, w in predictions)
    ensemble_pred = sum(pred * w for _, pred, w in predictions) / total_weight

    print("\n--- Component Forecasts ---")
    for name, pred, weight in predictions:
        print(f"  {name}: ${pred:.2f}M (weight: {weight:.0%})")

    print(f"\n--- ENSEMBLE FORECAST: ${ensemble_pred:.2f}M ---")

    return ensemble_pred


# ============================================================================
# MAIN EXECUTION
# ============================================================================

def main():
    """
    Main execution pipeline.
    """
    print("\n" + "=" * 80)
    print("CUBE REVENUE FORECASTING PIPELINE")
    print("=" * 80)
    print(f"Execution Time: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")

    # Step 1: Load data
    df_revenue = load_revenue_data(f'{INPUT_DIR}/revenue.csv')
    df_creditcard = load_creditcard_data(f'{INPUT_DIR}/creditcard.csv')

    # Step 2: Profile data
    df_revenue = profile_revenue_data(df_revenue)
    df_creditcard = profile_creditcard_data(df_creditcard)

    # Step 3: Feature engineering
    df_cc_agg, df_creditcard = extract_creditcard_features(df_creditcard, df_revenue)
    df_revenue = create_revenue_features(df_revenue)

    # Step 4: Seasonality analysis
    seasonal_indices = analyze_seasonality(df_revenue)

    # Step 5: Correlation analysis
    lag_corrs = analyze_correlations(df_revenue, df_cc_agg)

    # Step 6: Forecasting
    forecasts = {}

    # Time series forecast
    forecasts['time_series'] = forecast_time_series(df_revenue)

    # Regression forecast (if credit card data available)
    if df_cc_agg is not None:
        forecasts['regression'] = forecast_regression(df_revenue, df_cc_agg)

    # Naive seasonal forecast (same quarter last year)
    q4_2024 = df_revenue[df_revenue['period'] == '2025Q4']
    if len(q4_2024) == 0:
        q4_2024 = df_revenue[df_revenue['period'] == '2024Q4']
    if len(q4_2024) > 0:
        forecasts['naive'] = q4_2024['actual'].values[0]
        print(f"\n--- Naive Seasonal (2024Q4): ${forecasts['naive']:.2f}M ---")

    # Step 7: Ensemble forecast
    final_forecast = ensemble_forecast(forecasts)

    # Step 8: Summary
    print("\n" + "=" * 80)
    print("FINAL RESULTS SUMMARY")
    print("=" * 80)

    print(f"\n  Historical Data:")
    print(f"    - Revenue quarters: {len(df_revenue)}")
    print(f"    - Date range: {df_revenue['period'].iloc[0]} to {df_revenue['period'].iloc[-1]}")
    print(f"    - Latest revenue (2025Q3): ${df_revenue[df_revenue['period']=='2025Q3']['actual'].values[0]:.2f}M")

    print(f"\n  Q4 2025 Revenue Forecast:")
    if final_forecast:
        print(f"    - ENSEMBLE FORECAST: ${final_forecast:.2f}M")

    for name, forecast_data in forecasts.items():
        if isinstance(forecast_data, dict):
            print(f"    - {name.title()}: ${forecast_data.get('seasonal_forecast', forecast_data.get('seasonal_prediction', 'N/A')):.2f}M")
        else:
            print(f"    - {name.title()}: ${forecast_data:.2f}M")

    # Calculate confidence interval (simple approach)
    if final_forecast:
        # Use historical Q4 volatility as uncertainty measure
        q4_std = df_revenue[df_revenue['qtr'] == 4]['revenue'].std()
        margin = 1.96 * q4_std  # 95% CI

        print(f"\n  Uncertainty Estimate (95% CI):")
        print(f"    - Lower bound: ${final_forecast - margin:.2f}M")
        print(f"    - Upper bound: ${final_forecast + margin:.2f}M")

    print("\n" + "=" * 80)
    print("PIPELINE COMPLETE")
    print("=" * 80)

    return {
        'revenue_data': df_revenue,
        'creditcard_data': df_creditcard,
        'creditcard_aggregated': df_cc_agg,
        'forecasts': forecasts,
        'final_forecast': final_forecast
    }


if __name__ == "__main__":
    results = main()