CREATE TABLE public.carts (
	cart_id bigint NOT NULL,
	store bigint NOT NULL,
	movable boolean NOT NULL,
	customer character varying(100),
	UNIQUE(cart_id, store)
);

ALTER TABLE ONLY public.carts ADD CONSTRAINT carts_pkey PRIMARY KEY (cart_id, store);