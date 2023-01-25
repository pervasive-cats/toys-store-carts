CREATE TABLE public.carts (
	id bigserial NOT NULL,
	cart_id bigint NOT NULL,
	store bigint NOT NULL,
	is_movable boolean NOT NULL,
	customer character varying(100),
	UNIQUE(cart_id, store)
);

ALTER TABLE ONLY public.carts ADD CONSTRAINT carts_pkey PRIMARY KEY (id);