"""
Data Profiling and Exploratory Analysis
========================================
Detailed analysis of CUBE revenue and credit card data.
"""

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns
from datetime import datetime
import warnings
warnings.filterwarnings('ignore')

# Set style
plt.style.use('seaborn-v0_8-whitegrid')

INPUT_DIR = './input'
OUTPUT_DIR = './output'

def load_and_profile_all():
    """Load and profile all data sources."""

    # Load revenue data
    print("=" * 80)
    print("LOADING AND PROFILING DATA")
    print("=" * 80)

    df_revenue = pd.read_csv(f'{INPUT_DIR}/revenue.csv')
    print("\n--- REVENUE DATA ---")
    print(f"Shape: {df_revenue.shape}")
    print(f"Columns: {df_revenue.columns.tolist()}")
    print(df_revenue.head(10))

    # Parse revenue data
    df_revenue['year'] = df_revenue['period'].str[:4].astype(int)
    df_revenue['qtr'] = df_revenue['period'].str[-1].astype(int)

    print("\n--- REVENUE STATISTICS ---")
    print(df_revenue[['actual', 'pct_change']].describe())

    # Load credit card data
    print("\n--- CREDIT CARD DATA ---")

    # Try comma-separated first (based on actual file structure)
    try:
        df_cc = pd.read_csv(f'{INPUT_DIR}/creditcard.csv')
    except:
        df_cc = pd.read_csv(f'{INPUT_DIR}/creditcard.csv', sep='\t')

    print(f"Shape: {df_cc.shape}")
    print(f"Columns ({len(df_cc.columns)}):")
    for i, col in enumerate(df_cc.columns):
        print(f"  {i}: {col}")

    return df_revenue, df_cc


def analyze_revenue_trends(df_revenue):
    """Analyze revenue trends and patterns."""

    print("\n" + "=" * 80)
    print("REVENUE TREND ANALYSIS")
    print("=" * 80)

    # Overall statistics
    print("\n--- Overall Statistics ---")
    print(f"Total quarters: {len(df_revenue)}")
    print(f"Revenue range: ${df_revenue['actual'].min():.2f}M - ${df_revenue['actual'].max():.2f}M")
    print(f"Average revenue: ${df_revenue['actual'].mean():.2f}M")
    print(f"Total growth (first to last): {((df_revenue['actual'].iloc[-1] / df_revenue['actual'].iloc[0]) - 1) * 100:.1f}%")

    # Quarterly patterns
    print("\n--- Quarterly Patterns ---")
    quarterly_stats = df_revenue.groupby('qtr')['actual'].agg(['mean', 'std', 'min', 'max'])
    print(quarterly_stats)

    # Year-over-year growth
    print("\n--- Year-over-Year Growth ---")
    df_revenue_sorted = df_revenue.sort_values(['year', 'qtr'])
    df_revenue_sorted['yoy_growth'] = df_revenue_sorted.groupby('year')['actual'].pct_change(periods=4) * 100
    print(df_revenue_sorted[['period', 'actual', 'pct_change', 'yoy_growth']].tail(12))

    # Identify Q4 specifically
    print("\n--- Q4 Historical Performance ---")
    q4_data = df_revenue[df_revenue['qtr'] == 4][['period', 'actual', 'pct_change']]
    print(q4_data)

    return df_revenue_sorted


def analyze_creditcard_data(df_cc):
    """Analyze credit card transaction data."""

    print("\n" + "=" * 80)
    print("CREDIT CARD DATA ANALYSIS")
    print("=" * 80)

    # Find key columns
    print("\n--- Identifying Key Columns ---")

    # Use the actual column names from the data
    date_col = 'txn_date'
    amount_col = 'spend_raw'
    state_col = 'mem_state'
    income_col = 'mem_income_bin'
    consistent_col = 'constind'

    print(f"  Date column: {date_col}")
    print(f"  Amount column: {amount_col}")
    print(f"  State column: {state_col}")
    print(f"  Income column: {income_col}")
    print(f"  Consistent customer column: {consistent_col}")

    # Process data if date column found
    if date_col in df_cc.columns:
        df_cc['trans_date'] = pd.to_datetime(df_cc[date_col], errors='coerce')
        df_cc['year'] = df_cc['trans_date'].dt.year
        df_cc['quarter'] = df_cc['trans_date'].dt.quarter
        df_cc['year_quarter'] = df_cc['trans_date'].dt.to_period('Q')

        print(f"\n--- Date Range ---")
        print(f"  From: {df_cc['trans_date'].min()}")
        print(f"  To: {df_cc['trans_date'].max()}")

        # Aggregate by quarter
        if amount_col in df_cc.columns:
            print(f"\n--- Quarterly Spend Statistics ---")
            quarterly_spend = df_cc.groupby('year_quarter')[amount_col].agg(['sum', 'mean', 'count', 'std'])
            print(quarterly_spend)

            # Consistent customer analysis
            if consistent_col in df_cc.columns:
                print(f"\n--- Consistent Customer Analysis ---")
                df_cc['is_consistent'] = df_cc[consistent_col].astype(str).str.lower().isin(['true', '1', 'yes', 'h'])

                consistent_stats = df_cc.groupby('year_quarter').agg({
                    amount_col: 'sum',
                    'is_consistent': ['sum', 'mean']
                })
                consistent_stats.columns = ['total_spend', 'consistent_count', 'consistent_ratio']
                print(consistent_stats)

            # Geographic analysis
            if state_col in df_cc.columns:
                print(f"\n--- Top 10 States by Spend ---")
                state_spend = df_cc.groupby(state_col)[amount_col].sum().sort_values(ascending=False).head(10)
                print(state_spend)

            # Income analysis
            if income_col in df_cc.columns:
                print(f"\n--- Spend by Income Bin ---")
                income_spend = df_cc.groupby(income_col)[amount_col].agg(['sum', 'mean', 'count'])
                print(income_spend)

    return df_cc


def create_visualizations(df_revenue, df_cc):
    """Create visualizations for the analysis."""

    print("\n" + "=" * 80)
    print("CREATING VISUALIZATIONS")
    print("=" * 80)

    # Create output directory if needed
    import os
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    # Figure 1: Revenue Time Series
    fig, axes = plt.subplots(2, 2, figsize=(14, 10))

    # Plot 1: Revenue over time
    ax1 = axes[0, 0]
    ax1.plot(df_revenue['period'], df_revenue['actual'], marker='o', linewidth=2, markersize=4)
    ax1.set_title('CUBE Quarterly Revenue', fontsize=12, fontweight='bold')
    ax1.set_xlabel('Quarter')
    ax1.set_ylabel('Revenue ($M)')
    ax1.tick_params(axis='x', rotation=45)
    ax1.grid(True, alpha=0.3)

    # Highlight Q4 quarters
    q4_mask = df_revenue['qtr'] == 4
    ax1.scatter(df_revenue.loc[q4_mask, 'period'], df_revenue.loc[q4_mask, 'actual'],
                color='red', s=100, zorder=5, label='Q4')
    ax1.legend()

    # Plot 2: Growth Rate
    ax2 = axes[0, 1]
    ax2.bar(df_revenue['period'], df_revenue['pct_change'] * 100, color='steelblue', alpha=0.7)
    ax2.axhline(y=0, color='black', linestyle='-', linewidth=0.5)
    ax2.set_title('Quarter-over-Quarter Growth Rate', fontsize=12, fontweight='bold')
    ax2.set_xlabel('Quarter')
    ax2.set_ylabel('Growth Rate (%)')
    ax2.tick_params(axis='x', rotation=45)

    # Plot 3: Seasonal Box Plot
    ax3 = axes[1, 0]
    df_revenue.boxplot(column='actual', by='qtr', ax=ax3)
    ax3.set_title('Revenue Distribution by Quarter', fontsize=12, fontweight='bold')
    ax3.set_xlabel('Quarter')
    ax3.set_ylabel('Revenue ($M)')
    plt.suptitle('')  # Remove automatic title

    # Plot 4: YoY Growth
    ax4 = axes[1, 1]
    df_revenue_sorted = df_revenue.sort_values(['year', 'qtr'])
    df_revenue_sorted['yoy_growth'] = df_revenue_sorted['actual'].pct_change(periods=4) * 100
    ax4.plot(df_revenue_sorted['period'], df_revenue_sorted['yoy_growth'],
             marker='s', linewidth=2, markersize=4, color='green')
    ax4.axhline(y=0, color='black', linestyle='-', linewidth=0.5)
    ax4.set_title('Year-over-Year Growth', fontsize=12, fontweight='bold')
    ax4.set_xlabel('Quarter')
    ax4.set_ylabel('YoY Growth (%)')
    ax4.tick_params(axis='x', rotation=45)
    ax4.grid(True, alpha=0.3)

    plt.tight_layout()
    plt.savefig(f'{OUTPUT_DIR}/revenue_analysis.png', dpi=150, bbox_inches='tight')
    print(f"  Saved: {OUTPUT_DIR}/revenue_analysis.png")

    # Figure 2: Credit Card Analysis (if data available)
    if 'trans_date' in df_cc.columns and 'spend_raw' in df_cc.columns:
        fig2, axes2 = plt.subplots(2, 2, figsize=(14, 10))

        # Plot 1: Credit card spend over time
        ax1 = axes2[0, 0]
        quarterly_spend = df_cc.groupby('year_quarter')['spend_raw'].sum().reset_index()
        ax1.plot(quarterly_spend['year_quarter'].astype(str), quarterly_spend['spend_raw'],
                 marker='o', linewidth=2, markersize=4, color='purple')
        ax1.set_title('Credit Card Spend Over Time', fontsize=12, fontweight='bold')
        ax1.set_xlabel('Quarter')
        ax1.set_ylabel('Total Spend ($)')
        ax1.tick_params(axis='x', rotation=45)
        ax1.grid(True, alpha=0.3)

        # Plot 2: Transaction count
        ax2 = axes2[0, 1]
        quarterly_count = df_cc.groupby('year_quarter')['spend_raw'].count().reset_index()
        ax2.bar(quarterly_count['year_quarter'].astype(str), quarterly_count['spend_raw'],
                color='orange', alpha=0.7)
        ax2.set_title('Transaction Count by Quarter', fontsize=12, fontweight='bold')
        ax2.set_xlabel('Quarter')
        ax2.set_ylabel('Number of Transactions')
        ax2.tick_params(axis='x', rotation=45)

        # Plot 3: Consistent vs New Customers
        ax3 = axes2[1, 0]
        if 'is_consistent' in df_cc.columns:
            consistent_spend = df_cc.groupby(['year_quarter', 'is_consistent'])['spend_raw'].sum().unstack()
            consistent_spend.plot(kind='bar', ax=ax3, color=['coral', 'steelblue'])
            ax3.set_title('Spend: Consistent vs New Customers', fontsize=12, fontweight='bold')
            ax3.set_xlabel('Quarter')
            ax3.set_ylabel('Total Spend ($)')
            ax3.tick_params(axis='x', rotation=45)
            ax3.legend(['New', 'Consistent'])

        # Plot 4: Geographic distribution
        ax4 = axes2[1, 1]
        if 'mem_state' in df_cc.columns:
            top_states = df_cc.groupby('mem_state')['spend_raw'].sum().sort_values(ascending=False).head(10)
            ax4.barh(top_states.index, top_states.values, color='teal', alpha=0.7)
            ax4.set_title('Top 10 States by Spend', fontsize=12, fontweight='bold')
            ax4.set_xlabel('Total Spend ($)')

        plt.tight_layout()
        plt.savefig(f'{OUTPUT_DIR}/creditcard_analysis.png', dpi=150, bbox_inches='tight')
        print(f"  Saved: {OUTPUT_DIR}/creditcard_analysis.png")

    plt.close('all')
    print("  All visualizations complete!")


def main():
    """Main execution."""
    print("\n" + "=" * 80)
    print("CUBE DATA EXPLORATION AND PROFILING")
    print("=" * 80)
    print(f"Execution Time: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")

    # Load and profile data
    df_revenue, df_cc = load_and_profile_all()

    # Analyze revenue
    df_revenue = analyze_revenue_trends(df_revenue)

    # Analyze credit card data
    df_cc = analyze_creditcard_data(df_cc)

    # Create visualizations
    create_visualizations(df_revenue, df_cc)

    print("\n" + "=" * 80)
    print("ANALYSIS COMPLETE")
    print("=" * 80)


if __name__ == "__main__":
    main()