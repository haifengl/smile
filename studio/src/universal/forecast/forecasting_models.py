"""
Revenue Forecasting Models
==========================
Multiple approaches to forecast CUBE's Q4 2025 revenue.
"""

import pandas as pd
import numpy as np
from sklearn.linear_model import LinearRegression, Ridge, Lasso
from sklearn.ensemble import RandomForestRegressor, GradientBoostingRegressor
from sklearn.preprocessing import StandardScaler
from sklearn.model_selection import TimeSeriesSplit
from datetime import datetime
import warnings
warnings.filterwarnings('ignore')

INPUT_DIR = './input'
OUTPUT_DIR = './output'


def load_processed_data():
    """Load the processed feature dataset."""
    try:
        df = pd.read_csv(f'{OUTPUT_DIR}/features_merged.csv')
        print(f"Loaded processed data: {df.shape}")
        return df
    except:
        print("Processed data not found. Run feature_engineering.py first.")
        return None


def prepare_modeling_data(df, target_col='actual'):
    """
    Prepare data for modeling.
    Removes rows with missing target and selects features.
    """
    print("\nPreparing modeling data...")

    # Remove rows with missing target
    df_model = df.dropna(subset=[target_col]).copy()

    # Select feature columns (exclude identifiers and target)
    exclude_cols = ['period', 'year', 'qtr', 'quarter_date', 'year_quarter',
                    'actual', 'pct_change', 'quarter_end']
    feature_cols = [col for col in df_model.columns if col not in exclude_cols]

    # Remove columns with too many missing values
    valid_features = []
    for col in feature_cols:
        missing_pct = df_model[col].isna().sum() / len(df_model)
        if missing_pct < 0.5:  # Less than 50% missing
            valid_features.append(col)

    print(f"  Using {len(valid_features)} features")
    print(f"  Features: {valid_features[:10]}...")  # Show first 10

    X = df_model[valid_features].copy()
    y = df_model[target_col].copy()

    # Fill remaining missing values with forward fill then backward fill
    X = X.fillna(method='ffill').fillna(method='bfill')

    return X, y, valid_features


def train_time_series_model(X, y):
    """
    Train a simple time series model using trend and seasonality.
    """
    print("\n" + "=" * 80)
    print("MODEL 1: Time Series (Trend + Seasonality)")
    print("=" * 80)

    # Use time index and quarter dummies
    features = ['time_index', 'Q_2', 'Q_3', 'Q_4'] if 'time_index' in X.columns else []

    if not features:
        # Fallback: just use lag features
        features = [col for col in X.columns if 'lag' in col and 'revenue' in col]

    if not features:
        print("  Insufficient features for time series model")
        return None

    X_ts = X[features].copy()
    X_ts = X_ts.fillna(X_ts.mean())

    # Train model
    model = LinearRegression()
    model.fit(X_ts, y)

    # Evaluate
    train_score = model.score(X_ts, y)
    print(f"  Training R²: {train_score:.4f}")

    # Show coefficients
    print("\n  Feature coefficients:")
    for feat, coef in zip(features, model.coef_):
        print(f"    {feat}: {coef:.4f}")
    print(f"    intercept: {model.intercept_:.4f}")

    return {
        'model': model,
        'features': features,
        'type': 'time_series',
        'train_score': train_score
    }


def train_regression_model(X, y):
    """
    Train a regression model with all available features.
    """
    print("\n" + "=" * 80)
    print("MODEL 2: Ridge Regression (All Features)")
    print("=" * 80)

    # Use all available features
    X_reg = X.copy().fillna(X.mean())

    # Scale features
    scaler = StandardScaler()
    X_scaled = scaler.fit_transform(X_reg)

    # Train Ridge regression (regularized)
    model = Ridge(alpha=1.0)
    model.fit(X_scaled, y)

    # Evaluate
    train_score = model.score(X_scaled, y)
    print(f"  Training R²: {train_score:.4f}")

    # Cross-validation
    tscv = TimeSeriesSplit(n_splits=3)
    cv_scores = []
    for train_idx, val_idx in tscv.split(X_scaled):
        X_train, X_val = X_scaled[train_idx], X_scaled[val_idx]
        y_train, y_val = y.iloc[train_idx], y.iloc[val_idx]
        model_cv = Ridge(alpha=1.0)
        model_cv.fit(X_train, y_train)
        cv_scores.append(model_cv.score(X_val, y_val))

    print(f"  Cross-validation R²: {np.mean(cv_scores):.4f} (+/- {np.std(cv_scores):.4f})")

    # Feature importance
    print("\n  Top 10 Feature Importances (by coefficient magnitude):")
    importance = pd.DataFrame({
        'feature': X.columns,
        'importance': np.abs(model.coef_)
    }).sort_values('importance', ascending=False)
    print(importance.head(10).to_string(index=False))

    return {
        'model': model,
        'scaler': scaler,
        'features': list(X.columns),
        'type': 'ridge_regression',
        'train_score': train_score,
        'cv_score': np.mean(cv_scores)
    }


def train_ensemble_model(X, y):
    """
    Train an ensemble model (Gradient Boosting).
    """
    print("\n" + "=" * 80)
    print("MODEL 3: Gradient Boosting Ensemble")
    print("=" * 80)

    X_ens = X.copy().fillna(X.mean())

    # Train Gradient Boosting
    model = GradientBoostingRegressor(
        n_estimators=100,
        max_depth=3,
        learning_rate=0.1,
        random_state=42
    )
    model.fit(X_ens, y)

    # Evaluate
    train_score = model.score(X_ens, y)
    print(f"  Training R²: {train_score:.4f}")

    # Feature importance
    print("\n  Top 10 Feature Importances:")
    importance = pd.DataFrame({
        'feature': X.columns,
        'importance': model.feature_importances_
    }).sort_values('importance', ascending=False)
    print(importance.head(10).to_string(index=False))

    return {
        'model': model,
        'features': list(X.columns),
        'type': 'gradient_boosting',
        'train_score': train_score
    }


def generate_forecast(df, models, forecast_features):
    """
    Generate forecasts using trained models.
    """
    print("\n" + "=" * 80)
    print("GENERATING Q4 2025 FORECASTS")
    print("=" * 80)

    forecasts = {}

    # Prepare forecast features
    forecast_df = pd.DataFrame([forecast_features])

    for name, model_info in models.items():
        try:
            features = model_info['features']

            # Get feature values for forecast
            X_forecast = pd.DataFrame()
            for feat in features:
                if feat in forecast_df.columns:
                    X_forecast[feat] = forecast_df[feat]
                else:
                    X_forecast[feat] = np.nan

            # Fill missing with column means from training data
            X_forecast = X_forecast.fillna(X_forecast.mean())

            # Make prediction
            if model_info['type'] == 'ridge_regression':
                X_scaled = model_info['scaler'].transform(X_forecast)
                pred = model_info['model'].predict(X_scaled)[0]
            else:
                pred = model_info['model'].predict(X_forecast)[0]

            forecasts[name] = {
                'prediction': pred,
                'model_score': model_info.get('train_score', 'N/A'),
                'model_type': model_info['type']
            }

            print(f"\n  {name}:")
            print(f"    Prediction: ${pred:.2f}M")
            print(f"    Model R²: {model_info.get('train_score', 'N/A'):.4f}")

        except Exception as e:
            print(f"\n  {name}: Error - {str(e)}")

    return forecasts


def create_ensemble_forecast(forecasts, weights=None):
    """
    Create ensemble forecast by combining multiple models.
    """
    print("\n" + "=" * 80)
    print("ENSEMBLE FORECAST")
    print("=" * 80)

    if not forecasts:
        print("  No forecasts available")
        return None

    # Default weights based on model performance
    if weights is None:
        weights = {}
        for name, info in forecasts.items():
            score = info.get('model_score', 0.5)
            if isinstance(score, str):
                score = 0.5
            weights[name] = score

        # Normalize weights
        total = sum(weights.values())
        weights = {k: v / total for k, v in weights.items()}

    print("\n  Model weights:")
    for name, weight in weights.items():
        print(f"    {name}: {weight:.2%}")

    # Calculate weighted average
    ensemble_pred = sum(
        forecasts[name]['prediction'] * weights.get(name, 1/len(forecasts))
        for name in forecasts
    )

    print(f"\n  ENSEMBLE FORECAST: ${ensemble_pred:.2f}M")

    return ensemble_pred


def calculate_confidence_interval(forecasts, ensemble_pred):
    """
    Calculate confidence interval for the forecast.
    """
    print("\n" + "=" * 80)
    print("UNCERTAINTY ESTIMATION")
    print("=" * 80)

    predictions = [info['prediction'] for info in forecasts.values()]

    # Standard deviation of predictions
    pred_std = np.std(predictions)
    pred_range = max(predictions) - min(predictions)

    print(f"\n  Prediction range: ${min(predictions):.2f}M - ${max(predictions):.2f}M")
    print(f"  Standard deviation: ${pred_std:.2f}M")

    # 95% confidence interval (using prediction range as proxy)
    margin = 1.96 * pred_std

    print(f"\n  95% Confidence Interval:")
    print(f"    Lower bound: ${ensemble_pred - margin:.2f}M")
    print(f"    Upper bound: ${ensemble_pred + margin:.2f}M")

    return {
        'lower': ensemble_pred - margin,
        'upper': ensemble_pred + margin,
        'margin': margin
    }


def main():
    """Main execution."""
    print("\n" + "=" * 80)
    print("CUBE REVENUE FORECASTING MODELS")
    print("=" * 80)
    print(f"Execution Time: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")

    # Load processed data
    df = load_processed_data()

    if df is None:
        print("\nPlease run feature_engineering.py first to create features.")
        return

    # Prepare modeling data
    X, y, features = prepare_modeling_data(df)

    # Train models
    models = {}

    # Model 1: Time Series
    ts_model = train_time_series_model(X, y)
    if ts_model:
        models['time_series'] = ts_model

    # Model 2: Ridge Regression
    ridge_model = train_regression_model(X, y)
    if ridge_model:
        models['ridge_regression'] = ridge_model

    # Model 3: Gradient Boosting
    gb_model = train_ensemble_model(X, y)
    if gb_model:
        models['gradient_boosting'] = gb_model

    # Create forecast features (Q4 2025)
    from feature_engineering import create_forecast_features
    forecast_features = create_forecast_features(df, '2025Q4')

    # Generate forecasts
    forecasts = generate_forecast(df, models, forecast_features)

    # Ensemble forecast
    ensemble_pred = create_ensemble_forecast(forecasts)

    # Confidence interval
    if ensemble_pred:
        ci = calculate_confidence_interval(forecasts, ensemble_pred)

    # Summary
    print("\n" + "=" * 80)
    print("FINAL SUMMARY")
    print("=" * 80)

    print(f"\n  Q4 2025 Revenue Forecast:")
    print(f"    - Time Series Model: ${forecasts.get('time_series', {}).get('prediction', 'N/A'):.2f}M")
    print(f"    - Ridge Regression: ${forecasts.get('ridge_regression', {}).get('prediction', 'N/A'):.2f}M")
    print(f"    - Gradient Boosting: ${forecasts.get('gradient_boosting', {}).get('prediction', 'N/A'):.2f}M")
    print(f"    - ENSEMBLE: ${ensemble_pred:.2f}M")

    if ensemble_pred:
        print(f"\n  95% CI: ${ci['lower']:.2f}M - ${ci['upper']:.2f}M")

    print("\n" + "=" * 80)
    print("FORECASTING COMPLETE")
    print("=" * 80)


if __name__ == "__main__":
    main()