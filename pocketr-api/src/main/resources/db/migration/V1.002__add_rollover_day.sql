ALTER TABLE public.users
    ADD COLUMN rollover_day integer DEFAULT 1 NOT NULL;

ALTER TABLE public.household
    ADD COLUMN rollover_day integer DEFAULT 1 NOT NULL;
