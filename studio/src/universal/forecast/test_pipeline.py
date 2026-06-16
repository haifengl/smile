"""
Quick Test Script
=================
Verifies that all components work correctly.
"""

import sys
import os

# Add current directory to path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

print("=" * 80)
print("TESTING CUBE REVENUE FORECASTING PIPELINE")
print("=" * 80)

# Test 1: Load Revenue Data
print("\n[Test 1] Loading Revenue Data...")
try:
    import pandas as pd
    df_revenue = pd.read_csv('./input/revenue.csv')
    print(f"✓ Loaded {len(df_revenue)} revenue records")
    print(f"  Columns: {df_revenue.columns.tolist()}")
    print(f"  Date range: {df_revenue['period'].iloc[0]} to {df_revenue['period'].iloc[-1]}")
except Exception as e:
    print(f"✗ Failed: {e}")

# Test 2: Load Credit Card Data
print("\n[Test 2] Loading Credit Card Data...")
try:
    try:
        df_cc = pd.read_csv('./input/creditcard.csv')
    except:
        df_cc = pd.read_csv('./input/creditcard.csv', sep='\t')
    print(f"✓ Loaded {len(df_cc)} credit card records")
    print(f"  Columns: {len(df_cc.columns)} columns found")
except Exception as e:
    print(f"✗ Failed: {e}")

# Test 3: Parse Revenue Data
print("\n[Test 3] Parsing Revenue Data...")
try:
    df_revenue['year'] = df_revenue['period'].str[:4].astype(int)
    df_revenue['qtr'] = df_revenue['period'].str[-1].astype(int)
    print(f"✓ Parsed year and quarter")
    print(f"  Years: {df_revenue['year'].min()} - {df_revenue['year'].max()}")
    print(f"  Quarters: {sorted(df_revenue['qtr'].unique())}")
except Exception as e:
    print(f"✗ Failed: {e}")

# Test 4: Basic Statistics
print("\n[Test 4] Computing Basic Statistics...")
try:
    print(f"  Revenue range: ${df_revenue['actual'].min():.2f}M - ${df_revenue['actual'].max():.2f}M")
    print(f"  Average revenue: ${df_revenue['actual'].mean():.2f}M")
    print(f"  Total growth: {((df_revenue['actual'].iloc[-1] / df_revenue['actual'].iloc[0]) - 1) * 100:.1f}%")
    print("✓ Statistics computed")
except Exception as e:
    print(f"✗ Failed: {e}")

# Test 5: Q4 Analysis
print("\n[Test 5] Q4 Historical Analysis...")
try:
    q4_data = df_revenue[df_revenue['qtr'] == 4]
    print(f"  Q4 records: {len(q4_data)}")
    print(f"  Q4 average: ${q4_data['actual'].mean():.2f}M")
    print(f"  Q4 range: ${q4_data['actual'].min():.2f}M - ${q4_data['actual'].max():.2f}M")
    print("✓ Q4 analysis complete")
except Exception as e:
    print(f"✗ Failed: {e}")

# Test 6: Simple Forecast
print("\n[Test 6] Simple Q4 2025 Forecast...")
try:
    # Get recent trend
    recent = df_revenue.tail(8)
    x = list(range(len(recent)))
    y = recent['actual'].values

    # Simple linear regression
    slope = (len(x) * sum(a*b for a,b in zip(x,y)) - sum(x)*sum(y)) / (len(x)*sum(a*a for a in x) - sum(x)**2)
    intercept = (sum(y) - slope * sum(x)) / len(x)

    # Forecast next quarter
    next_x = len(recent)
    base_forecast = intercept + slope * next_x

    # Apply seasonal adjustment (Q4 vs average)
    q4_avg = df_revenue[df_revenue['qtr'] == 4]['actual'].mean()
    overall_avg = df_revenue['actual'].mean()
    seasonal_index = q4_avg / overall_avg

    seasonal_forecast = base_forecast * seasonal_index

    print(f"  Trend slope: ${slope:.3f}M per quarter")
    print(f"  Q4 seasonal index: {seasonal_index:.4f}")
    print(f"  Base forecast: ${base_forecast:.2f}M")
    print(f"  Seasonal forecast: ${seasonal_forecast:.2f}M")
    print("✓ Simple forecast complete")
except Exception as e:
    print(f"✗ Failed: {e}")

# Test 7: Credit Card Data Analysis
print("\n[Test 7] Credit Card Data Structure...")
try:
    # Use the actual column names from the data
    date_col = 'txn_date'
    amount_col = 'spend_raw'

    print(f"  Date column: {date_col}")
    print(f"  Amount column: {amount_col}")

    if date_col in df_cc.columns:
        df_cc['trans_date'] = pd.to_datetime(df_cc[date_col], errors='coerce')
        print(f"  Date range: {df_cc['trans_date'].min()} to {df_cc['trans_date'].max()}")

    print("✓ Credit card structure analyzed")
except Exception as e:
    print(f"✗ Failed: {e}")

print("\n" + "=" * 80)
print("ALL TESTS COMPLETE")
print("=" * 80)
print("\nTo run the full pipeline:")
print("  python run_forecast.py")
print()