-- Create member_lookup table for partition-aware member lookups
-- This script is idempotent and can be run multiple times safely

CREATE TABLE IF NOT EXISTS public.member_lookup (
    member_id        BIGINT PRIMARY KEY REFERENCES public.members(id),
    partition_number INTEGER NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create index for partition-based queries
CREATE INDEX IF NOT EXISTS idx_member_lookup_partition ON public.member_lookup(partition_number);

-- Add comment for documentation
COMMENT ON TABLE public.member_lookup IS 'Partition-aware member lookup table, referenced by member_alternate_ids';
