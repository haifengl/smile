"""
Feature Engineering for Revenue Forecasting
============================================
Creates features from revenue and credit card data for ML modeling.
"""

import pandas as pd
import numpy as np
from datetime import datetime
import warnings
warnings.filterwarnings('ignore')

INPUT_DIR = './input'
OUTPUT_DIR = './output'

def load_data():
    """Load both datasets."""
    df_revenue = pd.read_csv(f'{INPUT_DIR}/revenue.csv')
    df_revenue['year'] = df_revenue['period'].str[:4].astype(int)
    df_revenue['qtr'] = df_revenue['period'].str[-1].astype(int)

    try:
        df_cc = pd.read_csv(f'{INPUT_DIR}/creditcard.csv')
    except:
        df_cc = pd.read_csv(f'{INPUT_DIR}/creditcard.csv', sep='\t')

    return df_revenue, df_cc


def create_revenue_features(df):
    """
    Create time-series features from revenue data.
    """
    print("Creating revenue-based features...")

    df = df.sort_values(['year', 'qtr']).copy()

    # Lag features (previous quarters)
    for lag in [1, 2, 3, 4]:
        df[f'revenue_lag_{lag}'] = df['revenue'].shift(lag)
        df[f'growth_lag_{lag}'] = df['growth_rate'].shift(lag)

    # YoY features
    df['revenue_yoy_lag4'] = df['revenue'].shift(4)  # Same quarter last year
    df['revenue_yoy_change'] = (df['revenue'] - df['revenue_yoy_lag4']) / df['revenue_yoy_lag4']

    # Rolling statistics
    for window in [2, 4, 8]:
        df[f'revenue_ma{window}'] = df['revenue'].rolling(window=window).mean()
        df[f'revenue_std{window}'] = df['revenue'].rolling(window=window).std()

    # Growth momentum
    df['growth_momentum'] = df['growth_rate'] - df['growth_rate'].shift(1)
    df['growth_acceleration'] = df['growth_momentum'] - df['growth_momentum'].shift(1)

    # Quarter-over-quarter change
    df['revenue_qoq'] = df['revenue'].pct_change()

    # Trend features
    df['time_index'] = range(len(df))

    # Quarter dummies
    df = pd.get_dummies(df, columns=['qtr'], prefix='Q', drop_first=True)

    return df


def create_creditcard_features(df_cc, df_revenue):
    """
    Create features from credit card data aggregated by quarter.
    """
    print("Creating credit card-based features...")

    # Use the actual column names from the data
    date_col = 'txn_date'
    amount_col = 'spend_raw'
    state_col = 'mem_state'
    income_col = 'mem_income_bin'
    consistent_col = 'constind'

    if date_col not in df_cc.columns or amount_col not in df_cc.columns:
        print("  Warning: Required columns not found")
        return None

    # Parse dates
    df_cc['trans_date'] = pd.to_datetime(df_cc[date_col], errors='coerce')
    df_cc['year'] = df_cc['trans_date'].dt.year
    df_cc['quarter'] = df_cc['trans_date'].dt.quarter
    df_cc['year_quarter'] = df_cc['trans_date'].dt.to_period('Q')

    # Basic aggregations
    cc_agg = df_cc.groupby('year_quarter').agg({
        amount_col: ['sum', 'mean', 'count', 'std', 'median', 'min', 'max']
    })
    cc_agg.columns = ['_'.join(col).strip() for col in cc_agg.columns.values]
    cc_agg = cc_agg.reset_index()

    # Consistent customer features
    if consistent_col in df_cc.columns:
        df_cc['is_consistent'] = df_cc[consistent_col].astype(str).str.lower().isin(['true', '1', 'yes', 'h'])

        consistent_agg = df_cc.groupby('year_quarter').agg({
            amount_col: [
                ('consistent_spend', lambda x: x[df_cc.loc[x.index, 'is_consistent']].sum()),
                ('new_spend', lambda x: x[~df_cc.loc[x.index, 'is_consistent']].sum()),
                ('consistent_count', lambda x: x[df_cc.loc[x.index, 'is_consistent']].count()),
                ('new_count', lambda x: x[~df_cc.loc[x.index, 'is_consistent']].count()),
            ]
        })
        consistent_agg.columns = ['consistent_spend', 'new_spend', 'consistent_count', 'new_count']
        consistent_agg = consistent_agg.reset_index()

        cc_agg = cc_agg.merge(consistent_agg, on='year_quarter', how='left')
        cc_agg['consistent_ratio'] = cc_agg['consistent_spend'] / cc_agg[f'{amount_col}_sum']

    # Geographic concentration (Herfindahl index)
    if state_col in df_cc.columns:
        state_spend = df_cc.groupby(['year_quarter', state_col])[amount_col].sum().reset_index()
        state_spend['market_share'] = state_spend.groupby('year_quarter')[amount_col].transform(lambda x: x / x.sum())
        state_spend['hhi'] = state_spend['market_share'] ** 2
        hhi_agg = state_spend.groupby('year_quarter')['hhi'].sum().reset_index()
        hhi_agg.columns = ['year_quarter', 'geographic_concentration']

        cc_agg = cc_agg.merge(hhi_agg, on='year_quarter', how='left')

    # Income distribution features
    if income_col in df_cc.columns:
        income_spend = df_cc.groupby(['year_quarter', income_col])[amount_col].sum().unstack(fill_value=0)
        income_spend_pct = income_spend.div(income_spend.sum(axis=1), axis=0)

        # High income ratio (top income bracket / all)
        high_income_cols = [col for col in income_spend_pct.columns if '150' in str(col) or '100' in str(col)]
        if high_income_cols:
            cc_agg['high_income_ratio'] = income_spend_pct[high_income_cols].sum(axis=1)

    # Create lag features for credit card data
    for lag in [1, 2, 3, 4]:
        cc_agg[f'spend_lag_{lag}'] = cc_agg[f'{amount_col}_sum'].shift(lag)
        cc_agg[f'count_lag_{lag}'] = cc_agg[f'{amount_col}_count'].shift(lag)

    # Credit card growth rate
    cc_agg['cc_spend_growth'] = cc_agg[f'{amount_col}_sum'].pct_change()

    return cc_agg


def merge_datasets(df_revenue, df_cc_agg):
    """
    Merge revenue and credit card features into a single dataset.
    """
    print("Merging datasets...")

    # Create year_quarter for revenue data
    quarter_months = {1: '03', 2: '06', 3: '09', 4: '12'}
    df_revenue['quarter_date'] = df_revenue.apply(
        lambda x: f"{x['year']}-{quarter_months[x['qtr']]}", axis=1
    )
    df_revenue['year_quarter'] = pd.PeriodIndex(df_revenue['quarter_date'], freq='Q')

    # Merge
    df_merged = df_revenue.merge(df_cc_agg, on='year_quarter', how='left')

    return df_merged


def create_forecast_features(df, target_quarter='2025Q4'):
    """
    Create features for forecasting a specific quarter.
    """
    print(f"Creating forecast features for {target_quarter}...")

    # Parse target quarter
    target_year = int(target_quarter[:4])
    target_qtr = int(target_quarter[-1])

    # Get the last known quarter
    last_quarter = df[df['revenue'].notna()].iloc[-1]
    last_year = last_quarter['year']
    last_qtr_num = last_quarter['qtr']

    # Create forecast row
    forecast_row = {
        'quarter': target_quarter,
        'year': target_year,
        'qtr': target_qtr,
        'time_index': last_quarter['time_index'] + 1 if 'time_index' in df.columns else len(df),
    }

    # Add lag features (using the most recent available)
    for lag in [1, 2, 3, 4]:
        lag_year = target_year if target_qtr > lag else target_year - 1
        lag_qtr = target_qtr - lag if target_qtr > lag else 4 + (target_qtr - lag)

        # Find the corresponding row
        lag_row = df[(df['year'] == lag_year) & (df['qtr'] == lag_qtr)]
        if len(lag_row) > 0:
            lag_row = lag_row.iloc[0]
            forecast_row[f'revenue_lag_{lag}'] = lag_row['revenue']
            forecast_row[f'growth_lag_{lag}'] = lag_row['growth_rate']
        else:
            forecast_row[f'revenue_lag_{lag}'] = np.nan
            forecast_row[f'growth_lag_{lag}'] = np.nan

    # Add quarter dummies
    for q in range(1, 5):
        forecast_row[f'Q_{q}'] = 1 if q == target_qtr else 0

    # Add credit card features if available
    cc_cols = [col for col in df.columns if 'lag' in col and 'cc' in col.lower()]
    for col in cc_cols:
        # Use the most recent available credit card data
        recent_cc = df[df[col].notna()].iloc[-1] if len(df[df[col].notna()]) > 0 else None
        if recent_cc is not None:
            forecast_row[col] = recent_cc[col]

    return forecast_row


def main():
    """Main execution."""
    print("\n" + "=" * 80)
    print("FEATURE ENGINEERING FOR REVENUE FORECASTING")
    print("=" * 80)

    # Load data
    df_revenue, df_cc = load_data()

    # Create features
    df_revenue_feat = create_revenue_features(df_revenue)
    df_cc_agg = create_creditcard_features(df_cc, df_revenue)

    # Merge datasets
    if df_cc_agg is not None:
        df_merged = merge_datasets(df_revenue_feat, df_cc_agg)
    else:
        df_merged = df_revenue_feat

    # Display feature summary
    print("\n--- Feature Summary ---")
    print(f"Total features: {len(df_merged.columns)}")
    print(f"\nFeature list:")
    for col in df_merged.columns:
        print(f"  - {col}")

    # Create forecast features for Q4 2025
    forecast_features = create_forecast_features(df_merged, '2025Q4')

    print("\n--- Q4 2025 Forecast Features ---")
    for key, value in forecast_features.items():
        print(f"  {key}: {value}")

    # Save processed data
    import os
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    df_merged.to_csv(f'{OUTPUT_DIR}/features_merged.csv', index=False)
    print(f"\nSaved merged features to: {OUTPUT_DIR}/features_merged.csv")

    # Save feature summary
    feature_summary = pd.DataFrame({
        'feature': df_merged.columns,
        'dtype': df_merged.dtypes.values,
        'non_null': df_merged.notna().sum().values,
        'null_pct': (df_merged.isna().sum() / len(df_merged) * 100).values
    })
    feature_summary.to_csv(f'{OUTPUT_DIR}/feature_summary.csv', index=False)
    print(f"Saved feature summary to: {OUTPUT_DIR}/feature_summary.csv")

    print("\n" + "=" * 80)
    print("FEATURE ENGINEERING COMPLETE")
    print("=" * 80)

    return df_merged, forecast_features


if __name__ == "__main__":
    df_merged, forecast_features = main()