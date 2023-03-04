CREATE TABLE public.carts (
	cart_id bigint NOT NULL,
	store bigint NOT NULL,
	movable boolean NOT NULL,
	customer character varying(100),
	CONSTRAINT carts_pkey PRIMARY KEY (cart_id, store)
);
