-- Add preferred flag to member_addresses and member_emails tables

ALTER TABLE public.member_addresses ADD COLUMN IF NOT EXISTS preferred BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE public.member_emails ADD COLUMN IF NOT EXISTS preferred BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN public.member_addresses.preferred IS 'Indicates if this is the preferred address for the member';
COMMENT ON COLUMN public.member_emails.preferred IS 'Indicates if this is the preferred email for the member';
