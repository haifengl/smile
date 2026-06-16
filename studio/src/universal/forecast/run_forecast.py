"""
CUBE Revenue Forecasting - Main Runner
=======================================
Executes the complete pipeline: data loading, profiling, feature engineering,
modeling, and forecasting.

Usage:
    python run_forecast.py

Output:
    - Q4 2025 revenue forecast with confidence intervals
    - Visualization charts
    - Feature summary
    - Model performance metrics
"""

import sys
import os

# Add current directory to path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from datetime import datetime
import pandas as pd
import numpy as np

print("\n" + "=" * 80)
print("CUBE REVENUE FORECASTING PIPELINE")
print("=" * 80)
print(f"Start Time: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
print("=" * 80)

# ============================================================================
# STEP 1: EXPLORATORY DATA ANALYSIS
# ============================================================================
print("\n[STEP 1/5] Running Exploratory Data Analysis...")
print("-" * 80)

try:
    from exploratory_analysis import main as eda_main
    df_revenue, df_cc = eda_main()
    print("✓ EDA complete")
except Exception as e:
    print(f"✗ EDA failed: {e}")
    df_revenue, df_cc = None, None

# ============================================================================
# STEP 2: FEATURE ENGINEERING
# ============================================================================
print("\n[STEP 2/5] Running Feature Engineering...")
print("-" * 80)

try:
    from feature_engineering import main as fe_main
    df_merged, forecast_features = fe_main()
    print("✓ Feature engineering complete")
except Exception as e:
    print(f"✗ Feature engineering failed: {e}")
    df_merged, forecast_features = None, None

# ============================================================================
# STEP 3: MODEL TRAINING
# ============================================================================
print("\n[STEP 3/5] Training Forecasting Models...")
print("-" * 80)

try:
    from forecasting_models import (
        load_processed_data,
        prepare_modeling_data,
        train_time_series_model,
        train_regression_model,
        train_ensemble_model,
        generate_forecast,
        create_ensemble_forecast,
        calculate_confidence_interval
    )

    df = load_processed_data()
    if df is not None:
        X, y, features = prepare_modeling_data(df)

        models = {}

        # Train models
        ts_model = train_time_series_model(X, y)
        if ts_model:
            models['time_series'] = ts_model

        ridge_model = train_regression_model(X, y)
        if ridge_model:
            models['ridge_regression'] = ridge_model

        gb_model = train_ensemble_model(X, y)
        if gb_model:
            models['gradient_boosting'] = gb_model

        print("✓ Model training complete")
    else:
        models = {}
        print("⚠ No processed data available, skipping model training")

except Exception as e:
    print(f"✗ Model training failed: {e}")
    models = {}

# ============================================================================
# STEP 4: GENERATE FORECASTS
# ============================================================================
print("\n[STEP 4/5] Generating Q4 2025 Forecasts...")
print("-" * 80)

forecasts = {}
ensemble_pred = None
ci = None

if models and forecast_features is not None:
    try:
        from feature_engineering import create_forecast_features
        forecast_features = create_forecast_features(df, '2025Q4')
        forecasts = generate_forecast(df, models, forecast_features)
        ensemble_pred = create_ensemble_forecast(forecasts)

        if ensemble_pred:
            ci = calculate_confidence_interval(forecasts, ensemble_pred)

        print("✓ Forecast generation complete")
    except Exception as e:
        print(f"✗ Forecast generation failed: {e}")
else:
    print("⚠ Insufficient data for forecasting")

# ============================================================================
# STEP 5: FINAL SUMMARY
# ============================================================================
print("\n" + "=" * 80)
print("FINAL RESULTS")
print("=" * 80)

# Historical context
if df_revenue is not None:
    print("\n📊 HISTORICAL CONTEXT:")
    print(f"   - Data range: {df_revenue['period'].iloc[0]} to {df_revenue['period'].iloc[-1]}")
    print(f"   - Total quarters: {len(df_revenue)}")
    print(f"   - Latest revenue (2025Q3): ${df_revenue[df_revenue['period']=='2025Q3']['actual'].values[0]:.2f}M")

    # Q4 historical performance
    q4_data = df_revenue[df_revenue['qtr'] == 4]['actual']
    print(f"   - Historical Q4 average: ${q4_data.mean():.2f}M")
    print(f"   - Historical Q4 range: ${q4_data.min():.2f}M - ${q4_data.max():.2f}M")

# Forecast results
print("\n🔮 Q4 2025 FORECAST:")
if forecasts:
    for name, info in forecasts.items():
        pred = info['prediction']
        score = info.get('model_score', 'N/A')
        if isinstance(score, float):
            score = f"{score:.4f}"
        print(f"   - {name.replace('_', ' ').title()}: ${pred:.2f}M (R²: {score})")

if ensemble_pred:
    print(f"\n   📌 ENSEMBLE FORECAST: ${ensemble_pred:.2f}M")

    if ci:
        print(f"   📌 95% CONFIDENCE INTERVAL: ${ci['lower']:.2f}M - ${ci['upper']:.2f}M")

        # YoY growth estimate
        if df_revenue is not None:
            q4_2024 = df_revenue[df_revenue['quarter'] == '2024Q4']['revenue'].values[0]
            yoy_growth = (ensemble_pred - q4_2024) / q4_2024 * 100
            print(f"   📌 YoY Growth (vs 2024Q4): {yoy_growth:+.1f}%")

# Output files
print("\n📁 OUTPUT FILES:")
print("   - ./output/revenue_analysis.png")
print("   - ./output/creditcard_analysis.png")
print("   - ./output/features_merged.csv")
print("   - ./output/feature_summary.csv")

print("\n" + "=" * 80)
print(f"End Time: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
print("=" * 80)
print("\n✅ PIPELINE COMPLETE\n")