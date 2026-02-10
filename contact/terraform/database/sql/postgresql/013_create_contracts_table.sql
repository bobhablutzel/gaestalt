-- Create contracts table for contract definitions
-- This script is idempotent and can be run multiple times safely

CREATE TABLE IF NOT EXISTS public.contracts (
    id             UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    contract_name  VARCHAR(255) NOT NULL,
    company_id     UUID NOT NULL,
    company_name   VARCHAR(255) NOT NULL
);

-- Create index for company lookups
CREATE INDEX IF NOT EXISTS idx_contracts_company_id ON public.contracts(company_id);

-- Add comment for documentation
COMMENT ON TABLE public.contracts IS 'Contract definitions with company information';
