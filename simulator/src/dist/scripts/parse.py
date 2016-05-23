import pandas as pd
import numpy as np

parameters_df = pd.read_csv('parameters_modified.csv')
values_df = pd.read_csv('benchmark_values.csv')
columns = ['FacID', 'Call', 'Country', 'Ch']
parameters_df = parameters_df[columns]
values_df = values_df.rename(columns={'Callsigns':'Call'})

combined_df = pd.merge(parameters_df, values_df, on='Call')

volumes_df = pd.read_csv('volumes.csv')
combined_df = pd.merge(combined_df, volumes_df, on='FacID')
combined_df = combined_df.rename(columns={'Benchmark':'Value', 'volume':'Volume','Ch':'Channel'})
combined_df['Channel'] = combined_df['Channel'].astype(np.int32)

combined_df.to_csv('simulator.csv', index=False)