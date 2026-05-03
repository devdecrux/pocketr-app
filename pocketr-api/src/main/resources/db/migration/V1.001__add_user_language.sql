ALTER TABLE public.users
    ADD COLUMN language character varying(10) DEFAULT 'en' NOT NULL;
