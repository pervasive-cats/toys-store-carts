CREATE TABLE public.carts (
	cart_id bigserial NOT NULL,
	store bigint NOT NULL,
	movable boolean NOT NULL,
	customer character varying(100)
);

ALTER TABLE ONLY public.carts ADD CONSTRAINT carts_pkey PRIMARY KEY (cart_id);